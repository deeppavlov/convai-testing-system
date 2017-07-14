package org.pavlovai.dialog

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import org.pavlovai.communication._

import scala.concurrent.duration._
import scala.util.Try

/**
  * @author vadim
  * @since 06.07.17
  */
class Dialog(a: User, b: User, txt: String, gate: ActorRef) extends Actor with ActorLogging {
  import Dialog._

  private val timeout = Try(Duration.fromNanos(context.system.settings.config.getDuration("talk.talk_timeout").toNanos)).getOrElse(1.minutes)
  private val maxLen = Try(context.system.settings.config.getInt("talk.talk_length_max")).getOrElse(1000)

  private implicit val ec = context.dispatcher
  context.system.scheduler.scheduleOnce(timeout) { self ! EndDialog(None) }

  private var messagesCount: Int = 0

  override def receive: Receive = {
    case StartDialog =>
      def firstMessageFor(user: User, text: String): Endpoint.MessageFromDialog = user match {
        case u: Human => Endpoint.DeliverMessageToUser(u, text, Some(self.chatId))
        case u: Bot => Endpoint.DeliverMessageToUser(u, "/start " + text, Some(self.chatId))
      }
      if (a.id.hashCode < b.id.hashCode) {
        gate ! firstMessageFor(a, txt)
        gate ! firstMessageFor(b, txt)
      } else {
        gate ! firstMessageFor(b, txt)
        gate ! firstMessageFor(a, txt)
      }
    case PushMessageToTalk(from, text) =>
      val oppanent = if (from == a) b else if (from == b) a else throw new IllegalArgumentException(s"$from not in talk")
      gate ! Endpoint.DeliverMessageToUser(oppanent, text, Some(self.chatId))
      messagesCount += 1
      if (messagesCount > maxLen) self ! EndDialog(None)

    case EndDialog(u) =>
      val e1 = context.actorOf(EvaluationProcess.props(a, self, gate), name=s"evaluation-process-${self.chatId}-${a.id}")
      val e2 = context.actorOf(EvaluationProcess.props(b, self, gate), name=s"evaluation-process-${self.chatId}-${b.id}")
      context.become(onEvaluation(e1, e2))
      if (a.id.hashCode < b.id.hashCode) {
        e1 ! EvaluationProcess.StartEvaluation
        e2 ! EvaluationProcess.StartEvaluation
      } else {
        e2 ! EvaluationProcess.StartEvaluation
        e1 ! EvaluationProcess.StartEvaluation
      }
  }

  private var restEvaluations = 2
  def onEvaluation(aEvaluation: ActorRef, bEvaluation: ActorRef): Receive = {
    case EvaluationProcess.CompleteEvaluation(user, q, b, e) =>
      log.info("evaluation from {}: quality={}, breadth={}, engagement={}", user, q, b, e)
      restEvaluations -= 1
      if (restEvaluations == 0)
        self ! PoisonPill

    case EndDialog(u) => log.debug("already engagement")
    case m @ PushMessageToTalk(from, _) =>
      (if (from == a) aEvaluation else if (from == b) bEvaluation else throw new IllegalArgumentException(s"$from not in talk")) forward m
  }
}

object Dialog {
  def props(userA: User, userB: User, context: String, gate: ActorRef) = Props(new Dialog(userA, userB, context, gate))

  case class PushMessageToTalk(from: User, message: String)

  case object StartDialog
  case class EndDialog(sender: Option[User])

  implicit class DialogActorRef(ref: ActorRef) {
    val chatId: Int = ref.hashCode()
  }
}
