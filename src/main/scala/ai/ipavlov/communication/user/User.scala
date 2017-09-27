package ai.ipavlov.communication.user

import ai.ipavlov.communication.Endpoint
import ai.ipavlov.communication.user.User.TryShutdown
import ai.ipavlov.dialog.{Dialog, DialogFather}
import akka.actor.{ActorRef, FSM, PoisonPill, Props}

import scala.concurrent.duration._

/**
  * @author vadim
  * @since 06.07.17
  */
trait UserSummary {
  val id: String
}

case class Bot(token: String) extends UserSummary {
  val id: String = token
}

trait Human extends UserSummary

case class TelegramChat(id: String, username: Option[String]) extends Human {
  override def canEqual(a: Any): Boolean = a.isInstanceOf[TelegramChat]
  override def equals(that: Any): Boolean =
    that match {
      case that: TelegramChat => that.canEqual(this) && that.id == id
      case _ => false
    }
  override def hashCode: Int = { id.hashCode() }
}

case class FbChat(id: String) extends Human {
  override def canEqual(a: Any): Boolean = a.isInstanceOf[TelegramChat]
  override def equals(that: Any): Boolean =
    that match {
      case that: FbChat => that.canEqual(this) && that.id == id
      case _ => false
    }
  override def hashCode: Int = { id.hashCode() }
}

sealed trait UserState
case object Idle extends UserState
case object WaitDialogCreation extends UserState
case object InDialog extends UserState
case object OnEvaluation extends UserState

sealed trait State
case object Uninitialized extends State
case class DialogRef(dialog: ActorRef) extends State

class User(summary: Human, dialogDaddy: ActorRef, client: ActorRef) extends FSM[UserState, State] {
  import context.dispatcher

  context.system.scheduler.schedule(30.seconds, 30.seconds, self, TryShutdown)

  startWith(Idle, Uninitialized)

  when(Idle) {
    case Event(User.Begin, Uninitialized) =>
      log.info("!!!!!")
      goto(WaitDialogCreation) using Uninitialized

    case Event(User.Help, Uninitialized) =>
      client ! Client.ShowSystemNotification(summary.id, Messages.helpMessage)
      stay using Uninitialized

    case m =>
      log.info("????????" + m.toString)
      client ! Client.ShowSystemNotification(summary.id, Messages.notSupported)
      stay()
  }

  onTransition {
    case Idle -> WaitDialogCreation => dialogDaddy ! DialogFather.UserAvailable(summary, 1)
  }

  when(WaitDialogCreation) {
    case Event(Endpoint.SystemNotificationToUser(_, mes), Uninitialized) =>
      client ! Client.ShowSystemNotification(summary.id, mes)
      stay()

    case Event(Endpoint.ActivateTalkForUser(_, talk), Uninitialized) => goto(InDialog) using DialogRef(talk)

    case Event(TryShutdown, _) => stop()

    case _ => stay()
  }

  when(InDialog) {
    case Event(Endpoint.SystemNotificationToUser(_, mes), DialogRef(t)) =>
      client ! Client.ShowSystemNotification(summary.id, mes)
      stay()
    case Event(Endpoint.ChatMessageToUser(_, message: String, _, id: Int), DialogRef(_)) =>
      //TODO
      client ! Client.ShowChatMessage(summary.id, id.toString, message)
      stay()
    case Event(Endpoint.AskEvaluationFromHuman(_, text), DialogRef(_)) =>
      client ! Client.ShowEvaluationMessage(summary.id, text)
      stay()
    case Event(Endpoint.EndHumanDialog(_, _), DialogRef(_)) =>
      client ! Client.ShowLastNotificationInDialog(summary.id, Messages.lastNotificationInDialog)
      stay()

    case Event(User.AppendMessageToTalk(text), DialogRef(talk)) =>
      talk ! Dialog.PushMessageToTalk(summary, text)
      stay()
    case Event(User.EvaluateMessage(mid, evaluation), DialogRef(talk)) =>
      talk ! Dialog.EvaluateMessage(mid, evaluation)
      stay()

    case Event(Endpoint.FinishTalkForUser(_, _), DialogRef(_)) => goto(Idle) using Uninitialized

    case Event(User.End, DialogRef(t)) =>
      dialogDaddy ! DialogFather.UserLeave(summary)
      stay()

    case Event(TryShutdown, _) => stay()
  }
}

object User {
  def props(summary: Human, dialogDaddy: ActorRef, client: ActorRef) = Props(new User(summary, dialogDaddy, client))

  sealed trait UserCommand
  case object Begin extends UserCommand
  case object End extends UserCommand
  case object Help extends UserCommand
  case class EvaluateMessage(messageId: Int, evaluation: Int)
  case class AppendMessageToTalk(text: String) extends UserCommand

  private case object TryShutdown
}