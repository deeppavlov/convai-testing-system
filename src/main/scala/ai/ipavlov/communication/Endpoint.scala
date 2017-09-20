package ai.ipavlov.communication

import ai.ipavlov.communication.rest.{BotEndpoint, Routes}
import ai.ipavlov.communication.telegram.{BotWorker, TelegramEndpoint}
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

  private val telegramGate = context.actorOf(TelegramEndpoint.props(self, storage), "telegram-gate")
  private val botGate = context.actorOf(BotEndpoint.props(self), "bot-gate")

  private val telegramToken = Try(context.system.settings.config.getString("telegram.token")).getOrElse("unknown")
  private val facebookSecret = Try(context.system.settings.config.getString("fbmessager.secret")).getOrElse("unknown")
  private val facebookToken = Try(context.system.settings.config.getString("fbmessager.token")).getOrElse("unknown")
  private val facebookPageAccessToken = Try(context.system.settings.config.getString("fbmessager.pageAccessToken")).getOrElse("unknown")
  private val telegramWebhook = Try(context.system.settings.config.getString("telegram.webhook")).getOrElse {
    log.error("telegram.webhook not set!")
    "https://localhost"
  }

  private implicit val mat: ActorMaterializer = ActorMaterializer()

  private val bot = new BotWorker(context.system, telegramGate,
    Routes.route(botGate, facebookSecret, facebookToken, facebookPageAccessToken)(mat, context.dispatcher, context.system),
    telegramToken, telegramWebhook).run()

  private def initialized(talkConstructor: ActorRef): Receive = {
    case message @ ChatMessageToUser(_: TelegramChat, _, _, _) => telegramGate forward message
    case message @ ChatMessageToUser(_: Bot, _, _, _) => botGate forward message

    case m @ ActivateTalkForUser(_: TelegramChat, _) => telegramGate forward m
    case m @ ActivateTalkForUser(_: Bot, _) => botGate forward m

    case m @ FinishTalkForUser(_: TelegramChat, _) => telegramGate forward m
    case m @ FinishTalkForUser(_: Bot, _) => botGate forward m

    case m: AskEvaluationFromHuman => telegramGate forward m
    case m: SystemNotificationToUser => telegramGate forward m
    case m: EndHumanDialog => telegramGate forward m

    case m: DialogFather.UserAvailable => talkConstructor forward m
    case m: DialogFather.UserLeave => talkConstructor forward m
    case m: DialogFather.CreateTestDialogWithBot => talkConstructor forward m

    case m: ChancelTestDialog => telegramGate forward m
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
  case class ChatMessageToUser(receiver: User, message: String, fromDialogId: Int, id: Int) extends MessageFromDialog
  trait SystemNotification extends MessageFromDialog
  case class AskEvaluationFromHuman(receiver: Human, question: String) extends SystemNotification
  case class EndHumanDialog(receiver: Human, text: String) extends SystemNotification
  case class SystemNotificationToUser(receiver: User, message: String) extends SystemNotification

  case class ActivateTalkForUser(user: User, talk: ActorRef)
  case class FinishTalkForUser(user: User, talk: ActorRef)

  case class SetDialogFather(daddy: ActorRef)

  case class ChancelTestDialog(user: Human, cause: String)
}

