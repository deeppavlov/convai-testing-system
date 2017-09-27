package ai.ipavlov.communication

import ai.ipavlov.communication.fbmessager.FBClient
import ai.ipavlov.communication.rest.{BotEndpoint, Routes}
import ai.ipavlov.communication.telegram.{BotWorker, TelegramEndpoint}
import ai.ipavlov.communication.user._
import akka.actor.{Actor, ActorLogging, ActorRef, Props, Stash}
import akka.stream.ActorMaterializer

import scala.util.Try

/**
  * @author vadim
  * @since 11.07.17
  */
class Endpoint(storage: ActorRef) extends Actor with ActorLogging with Stash {
  import Endpoint._

  private val telegramGate = context.actorOf(TelegramEndpoint.props(self, storage), "telegram-gate")
  private val botGate = context.actorOf(BotEndpoint.props(self), "bot-gate")
  private val facebookPageAccessToken = Try(context.system.settings.config.getString("fbmessager.pageAccessToken")).getOrElse("unknown")
  //private val fbGate = context.actorOf(FBEndpoint.props(self, storage, facebookPageAccessToken), "fb-gate")

  private val telegramToken = Try(context.system.settings.config.getString("telegram.token")).getOrElse("unknown")
  private val facebookSecret = Try(context.system.settings.config.getString("fbmessager.secret")).getOrElse("unknown")
  private val facebookToken = Try(context.system.settings.config.getString("fbmessager.token")).getOrElse("unknown")
  private val telegramWebhook = Try(context.system.settings.config.getString("telegram.webhook")).getOrElse {
    log.error("telegram.webhook not set!")
    "https://localhost"
  }

  private implicit val mat: ActorMaterializer = ActorMaterializer()

  private val bot = new BotWorker(context.system, telegramGate,
    Routes.route(botGate, self, facebookSecret, facebookToken, facebookPageAccessToken)(mat, context.dispatcher, context.system),
    telegramToken, telegramWebhook).run()

  /*private def initialized(talkConstructor: ActorRef): Receive = {
    case message @ ChatMessageToUser(_: TelegramChat, _, _, _) => telegramGate forward message
    case message @ ChatMessageToUser(_: Bot, _, _, _) => botGate forward message
    case message @ ChatMessageToUser(_: FbChat, _, _, _) => fbGate forward message

    case m @ ActivateTalkForUser(_: TelegramChat, _) => telegramGate forward m
    case m @ ActivateTalkForUser(_: Bot, _) => botGate forward m
    case m @ ActivateTalkForUser(_: FbChat, _) => fbGate forward m

    case m @ FinishTalkForUser(_: TelegramChat, _) => telegramGate forward m
    case m @ FinishTalkForUser(_: Bot, _) => botGate forward m
    case m @ FinishTalkForUser(_: FbChat, _) => fbGate forward m

    case m@AskEvaluationFromHuman(_: TelegramChat, _) => telegramGate forward m
    case m@SystemNotificationToUser(_: TelegramChat, _) => telegramGate forward m
    case m@EndHumanDialog(_: TelegramChat, _) => telegramGate forward m
    case m@AskEvaluationFromHuman(_: FbChat, _) => fbGate forward m
    case m@SystemNotificationToUser(_: FbChat, _) => fbGate forward m
    case m@EndHumanDialog(_: FbChat, _) => fbGate forward m

    case m: DialogFather.UserAvailable => talkConstructor forward m
    case m: DialogFather.UserLeave => talkConstructor forward m
    case m: DialogFather.CreateTestDialogWithBot => talkConstructor forward m

    case m: ChancelTestDialog => telegramGate forward m
  }*/


  private def initialized(talkConstructor: ActorRef): Receive = {
    val fbClient = context.actorOf(FBClient.props(talkConstructor, storage, facebookPageAccessToken), name="fbClient")

    def user(h: Human): ActorRef = h match {
      case _: FbChat => context.child("fb-" + h.chatId).getOrElse(context.actorOf(User.props(h, talkConstructor, fbClient)))
    }

    {
      case m @ ChatMessageToUser(h: FbChat, _, _, _) => user(h) forward m
      case m @ SystemNotificationToUser(h: FbChat, _) => user(h) forward m

      case m @ ActivateTalkForUser(h: FbChat, _) => user(h) forward m
      case m @ FinishTalkForUser(h: FbChat, _) => user(h) forward m
      case m @ AskEvaluationFromHuman(h: FbChat, _) => user(h) forward m
      case m @ EndHumanDialog(h: FbChat, _) => user(h) forward m

      case m @ MessageFromUser(h: FbChat, text) if text.trim == "/begin" => user(h) ! User.Begin
      case m @ MessageFromUser(h: FbChat, text) if text.trim == "/end" => user(h) ! User.End
      case m @ MessageFromUser(h: FbChat, text) if text.trim == "/help" => user(h) ! User.Help
      case m @ MessageFromUser(h: FbChat, text) => user(h) ! User.AppendMessageToTalk(text)
      case m @ EvaluateFromUser(h: FbChat, mid, eval) => user(h) ! User.EvaluateMessage(mid, eval)
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
  case class ChatMessageToUser(receiver: UserSummary, message: String, fromDialogId: Int, id: Int) extends MessageFromDialog
  trait SystemNotification extends MessageFromDialog
  case class AskEvaluationFromHuman(receiver: Human, question: String) extends SystemNotification
  case class EndHumanDialog(receiver: Human, text: String) extends SystemNotification
  case class SystemNotificationToUser(receiver: UserSummary, message: String) extends SystemNotification

  case class ActivateTalkForUser(user: UserSummary, talk: ActorRef)
  case class FinishTalkForUser(user: UserSummary, talk: ActorRef)

  case class SetDialogFather(daddy: ActorRef)

  case class ChancelTestDialog(user: Human, cause: String)


  case class MessageFromUser(user: UserSummary, text: String)
  case class EvaluateFromUser(user: UserSummary, mid: Int, eval: Int)

}

