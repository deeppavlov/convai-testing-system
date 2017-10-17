package ai.ipavlov.communication.user

import ai.ipavlov.communication.Endpoint
import ai.ipavlov.communication.user.User.{TryShutdown, UserCommand}
import ai.ipavlov.dialog.{Dialog, DialogFather}
import akka.actor.{ActorRef, FSM, LoggingFSM, Props}

import scala.concurrent.duration._

/**
  * @author vadim
  * @since 06.07.17
  */
trait UserSummary {
  val address: String
}

case class Bot(token: String) extends UserSummary {
  val address: String = token
}

trait Human extends UserSummary {
  val username: String
}
case class TelegramChat(address: String, username: String) extends Human
case class FbChat(address: String, username: String) extends Human

sealed trait UserState
case object Idle extends UserState
case object WaitDialogCreation extends UserState
case object InDialog extends UserState
case object OnEvaluation extends UserState
case object BotTestDialogCreation extends UserState
case object BotTesting extends UserState

sealed trait State
case object Uninitialized extends State
case class DialogRef(dialog: ActorRef) extends State
case class BotUnderTest(botId: String) extends State
case class BotTestingData(botId: String, dialog: ActorRef) extends State

class User(summary: Human, dialogDaddy: ActorRef, client: ActorRef) extends LoggingFSM[UserState, State] {
  override def logDepth = 12
  import context.dispatcher

  context.system.scheduler.schedule(30.seconds, 30.seconds, self, TryShutdown)

  startWith(Idle, Uninitialized)

  when(Idle) {
    case Event(User.Begin, Uninitialized) =>
      dialogDaddy ! DialogFather.UserAvailable(summary, 1)
      goto(WaitDialogCreation) using Uninitialized

    case Event(User.Help, Uninitialized) =>
      client ! Client.ShowHelpMessage(summary.address, isShort = false)
      stay()

    case Event(Endpoint.SystemNotificationToUser(_, mes), Uninitialized) =>
      client ! Client.ShowSystemNotification(summary.address, mes)
      stay()

    case Event(User.Test(botId), Uninitialized) =>
      dialogDaddy ! DialogFather.CreateTestDialogWithBot (summary, botId)
      goto(BotTestDialogCreation) using BotUnderTest(botId)

    case Event(TryShutdown, _) => stop()

    case _ =>
      client ! Client.ShowHelpMessage(summary.address)
      stay()
  }

  when(WaitDialogCreation) {
    case Event(Endpoint.SystemNotificationToUser(_, mes), Uninitialized) =>
      client ! Client.ShowSystemNotification(summary.address, mes)
      stay()

    case Event(Endpoint.ActivateTalkForUser(_, talk), Uninitialized) =>
      goto(InDialog) using DialogRef(talk)

    case Event(TryShutdown, _) => stay()

    case Event(_: UserCommand, Uninitialized) =>
      client ! Client.ShowSystemNotification(summary.address, Messages.pleaseWait)
      stay()
  }

  when(InDialog) {
    case Event(Endpoint.SystemNotificationToUser(_, mes), DialogRef(t)) =>
      client ! Client.ShowSystemNotification(summary.address, mes)
      stay()
    case Event(Endpoint.ChatMessageToUser(_, face, message, _, id), DialogRef(_)) =>
      //TODO
      client ! Client.ShowChatMessage(summary.address, face, id.toString, message)
      stay()
    case Event(Endpoint.AskEvaluationFromHuman(_, text), DialogRef(_)) =>
      client ! Client.ShowEvaluationMessage(summary.address, text)
      stay()
    case Event(Endpoint.EndHumanDialog(_, text), DialogRef(_)) =>
      client ! Client.ShowLastNotificationInDialog(summary.address, text)
      stay()

    case Event(User.AppendMessageToTalk(text), DialogRef(talk)) =>
      talk ! Dialog.PushMessageToTalk(summary, text)
      stay()
    case Event(User.EvaluateMessage(mid, evaluation), DialogRef(talk)) =>
      talk ! Dialog.EvaluateMessage(mid, evaluation)
      stay()

    case Event(Endpoint.FinishTalkForUser(_, _), DialogRef(_)) =>
      goto(Idle) using Uninitialized

    case Event(User.End, DialogRef(t)) =>
      dialogDaddy ! DialogFather.UserLeave(summary)
      stay()

    case Event(TryShutdown, _) => stay()
  }

  when(BotTestDialogCreation) {
    case Event(Endpoint.SystemNotificationToUser(_, mes), Uninitialized) =>
      client ! Client.ShowSystemNotification(summary.address, mes)
      stay()

    case Event(Endpoint.ActivateTalkForUser(_, talk), BotUnderTest(botId)) =>
      goto(BotTesting) using BotTestingData(botId, talk)

    case Event(TryShutdown, _) => stay()

    case Event(Endpoint.ChancelTestDialog(_, cause), _) =>
      client ! Client.ShowSystemNotification(summary.address, cause)
      goto(Idle) using Uninitialized
  }

  when(BotTesting) {
    case Event(Endpoint.SystemNotificationToUser(_, mes), _) =>
      client ! Client.ShowSystemNotification(summary.address, mes)
      stay()
    case Event(Endpoint.ChatMessageToUser(_, face, message, _, id), _) =>
      //TODO
      client ! Client.ShowChatMessage(summary.address, face, id.toString, message)
      stay()
    case Event(Endpoint.AskEvaluationFromHuman(_, text), _) =>
      client ! Client.ShowEvaluationMessage(summary.address, text)
      stay()
    case Event(Endpoint.EndHumanDialog(_, text), _) =>
      client ! Client.ShowLastNotificationInDialog(summary.address, text)
      stay()

    case Event(User.AppendMessageToTalk(text), BotTestingData(_, talk)) =>
      talk ! Dialog.PushMessageToTalk(summary, text)
      stay()
    case Event(User.EvaluateMessage(mid, evaluation), BotTestingData(_, talk)) =>
      talk ! Dialog.EvaluateMessage(mid, evaluation)
      stay()

    case Event(Endpoint.FinishTalkForUser(_, _), _) =>
      goto(Idle) using Uninitialized

    case Event(User.End, _) =>
      dialogDaddy ! DialogFather.UserLeave(summary)
      stay()

    case Event(Endpoint.ChancelTestDialog(_, cause), _) =>
      log.info("test dialog canceled: {}", cause)
      goto(Idle) using Uninitialized

    case Event(TryShutdown, _) => stay()
  }

  whenUnhandled {
    case Event(_: UserCommand, Uninitialized) =>
      client ! Client.ShowSystemNotification(summary.address, Messages.youCantDoItNow)
      stay()

    case Event(event, data) =>
      log.warning("Received unhandled event: {} in state {}", event, stateName)
      client ! Client.ShowSystemNotification(summary.address, Messages.notSupported)
      stay
  }

  onTermination {
    case StopEvent(FSM.Failure(_), state, data) =>
      val lastEvents = getLog.mkString("\n\t")
      log.warning("Failure in state " + state + " with data " + data + "\n" +
        "Events leading up to this point:\n\t" + lastEvents)
  }

  initialize()
}

object User {
  def props(summary: Human, dialogDaddy: ActorRef, client: ActorRef) = Props(new User(summary, dialogDaddy, client))

  sealed trait UserCommand
  case object Begin extends UserCommand
  case object End extends UserCommand
  case object Help extends UserCommand
  case class  Test(botId: String) extends UserCommand

  case class EvaluateMessage(messageId: String, evaluation: Int)
  case class AppendMessageToTalk(text: String) extends UserCommand

  private case object TryShutdown
}