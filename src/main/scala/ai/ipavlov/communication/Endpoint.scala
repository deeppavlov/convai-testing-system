package ai.ipavlov.communication

import ai.ipavlov.communication.fbmessenger.FBClient
import ai.ipavlov.communication.rest.{BotEndpoint, Routes}
import ai.ipavlov.communication.telegram.{BotWorker, TelegramClient}
import ai.ipavlov.communication.user._
import ai.ipavlov.dialog.DialogFather
import akka.actor.{Actor, ActorLogging, ActorRef, Props, Stash}
import akka.stream.ActorMaterializer

import scala.util.Try

/**
  * @author vadim
  * @since 11.07.17
  */
class Endpoint(storage: ActorRef) extends Actor with ActorLogging with Stash {
  import Endpoint._

  private val botGate = context.actorOf(BotEndpoint.props(self), "bot-gate")
  private val facebookPageAccessToken = Try(context.system.settings.config.getString("fbmessenger.pageAccessToken")).getOrElse("unknown")

  private val telegramToken = Try(context.system.settings.config.getString("telegram.token")).getOrElse("unknown")
  private val facebookSecret = Try(context.system.settings.config.getString("fbmessenger.secret")).getOrElse("unknown")
  private val facebookToken = Try(context.system.settings.config.getString("fbmessenger.token")).getOrElse("unknown")
  private val telegramWebhook = Try(context.system.settings.config.getString("telegram.webhook")).getOrElse {
    log.error("telegram.webhook not set!")
    "https://localhost"
  }

  private implicit val mat: ActorMaterializer = ActorMaterializer()

  private val bot = new BotWorker(context.system, self,
    Routes.route(botGate, self, facebookSecret, facebookToken, facebookPageAccessToken)(mat, context.dispatcher, context.system, log),
    telegramToken, telegramWebhook)
  bot.run()
  private val tgClient = context.actorOf(TelegramClient.props(bot.request), name="tg-client")
  private val fbClient = context.actorOf(FBClient.props(facebookPageAccessToken), name="fb-client")

  private def initialized(talkConstructor: ActorRef): Receive = {

    def user(h: Human): ActorRef = {
      h match {
        case _: FbChat => context.child("fb-" + h.address).getOrElse(context.actorOf(User.props(h, talkConstructor, fbClient), name = "fb-" + h.address))
        case _: TelegramChat => context.child("tg-" + h.address).getOrElse(context.actorOf(User.props(h, talkConstructor, tgClient), name = "tg-" + h.address))
      }
    }

    {
      case m @ ShowChatMessageToUser(h: Human, _, _, _, _) => user(h) forward m
      case m @ ShowSystemNotificationToUser(h: Human, _) => user(h) forward m
      case m @ ShowContextToUser(h: Human, _) => user(h) forward m
      case m @ ActivateTalkForUser(h: Human, _) => user(h) forward m
      case m @ FinishTalkForUser(h: Human, _) => user(h) forward m
      case m @ AskEvaluationFromHuman(h: Human, _) => user(h) forward m
      case m @ EndHumanDialog(h: Human, _) => user(h) forward m
      case m @ ChancelTestDialog(h: Human, cause) => user(h) forward m
      case m @ MessageFromUser(h: Human, text) if text.trim == "/begin" => user(h) ! User.Begin
      case m @ MessageFromUser(h: Human, text) if text.trim == "/end" => user(h) ! User.End
      case m @ MessageFromUser(h: Human, text) if text.trim == "/help" => user(h) ! User.Help
      case m @ MessageFromUser(h: Human, text) if text.trim.startsWith("/test") => user(h) ! User.Test(text.trim.substring("/test".length).trim)
      case m @ MessageFromUser(h: Human, text) => user(h) ! User.AppendMessageToTalk(text)
      case m @ EvaluateFromUser(h: Human, mid, eval) => user(h) ! User.EvaluateMessage(mid, eval)


      case message @ ShowChatMessageToUser(_: Bot, _, _, _, _) => botGate forward message
      case m @ ActivateTalkForUser(_: Bot, _) => botGate forward m
      case m @ FinishTalkForUser(_: Bot, _) => botGate forward m
      case m: DialogFather.UserAvailable => talkConstructor forward m
      case m: DialogFather.UserLeave => talkConstructor forward m
      case m: DialogFather.CreateTestDialogWithBot => talkConstructor forward m

      //TODO do this for other components
      case s @ Configure => botGate forward s
    }
  }

  private val uninitialized: Receive = {
    case SetDialogFather(daddy) =>
      context.become(initialized(daddy))
      unstashAll()
      log.debug("endpoint actor initialized")
    case m =>
      stash()
      log.debug("endpoint actor not initialized")
  }

  override def receive: Receive = uninitialized
}

object Endpoint {
  def props(storage: ActorRef): Props = Props(new Endpoint(storage))

  sealed trait MessageFromDialog
  case class ShowChatMessageToUser(receiver: UserSummary, face: String, message: String, fromDialogId: Int, id: String) extends MessageFromDialog
  trait SystemNotification extends MessageFromDialog
  case class AskEvaluationFromHuman(receiver: Human, question: String) extends SystemNotification
  case class EndHumanDialog(receiver: Human, text: String) extends SystemNotification
  case class ShowSystemNotificationToUser(receiver: UserSummary, message: String) extends SystemNotification
  case class ShowContextToUser(receiver: UserSummary, message: String) extends SystemNotification

  case class ActivateTalkForUser(user: UserSummary, talk: ActorRef)
  case class FinishTalkForUser(user: UserSummary, talk: ActorRef)

  case class SetDialogFather(daddy: ActorRef)

  case class ChancelTestDialog(user: Human, cause: String)


  case class MessageFromUser(user: UserSummary, text: String)
  case class EvaluateFromUser(user: UserSummary, mid: String, eval: Int)

  case object Configure
}

