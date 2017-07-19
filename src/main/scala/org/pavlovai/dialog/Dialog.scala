package org.pavlovai.dialog

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import org.pavlovai.communication._

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._
import scala.util.Try
import scala.util.control.NoStackTrace

/**
  * @author vadim
  * @since 06.07.17
  */
class Dialog(a: User, b: User, txtContext: String, gate: ActorRef, database: ActorRef) extends Actor with ActorLogging {
  import Dialog._

  private val timeout = Try(Duration.fromNanos(context.system.settings.config.getDuration("talk.talk_timeout").toNanos)).getOrElse(1.minutes)
  private val maxLen = Try(context.system.settings.config.getInt("talk.talk_length_max")).getOrElse(1000)

  private implicit val ec = context.dispatcher
  context.system.scheduler.scheduleOnce(timeout) { self ! EndDialog }

  private val history: ArrayBuffer[(User, String)] = new ArrayBuffer[(User, String)](maxLen)

  override def receive: Receive = {
    case StartDialog =>
      def firstMessageFor(user: User, text: String): Endpoint.MessageFromDialog = user match {
        case u: Human => Endpoint.SystemNotificationToUser(u, text)
        case u: Bot => Endpoint.ChatMessageToUser(u, "/start " + text, self.chatId)
      }

      gate ! firstMessageFor(a, txtContext)
      gate ! firstMessageFor(b, txtContext)

    case PushMessageToTalk(from, text) =>
      val oppanent = if (from == a) b else if (from == b) a else throw new IllegalArgumentException(s"$from not in talk")
      gate ! Endpoint.ChatMessageToUser(oppanent, text, self.chatId)
      history.append(from -> text)
      if (history.size > maxLen) self ! EndDialog

    case EndDialog =>
      val e1 = context.actorOf(EvaluationProcess.props(a, self, gate), name=s"evaluation-process-${self.chatId}-${a.id}")
      e1 ! EvaluationProcess.StartEvaluation
      val e2 = context.actorOf(EvaluationProcess.props(b, self, gate), name=s"evaluation-process-${self.chatId}-${b.id}")
      e2 ! EvaluationProcess.StartEvaluation
      context.become(onEvaluation(e1, e2))

    case EvaluateMessage(messageHash, category) =>
      log.info("rated message {} with {}", messageHash, category)
      sender ! Ok
  }

  private val evaluations: mutable.Set[(User, (Int, Int, Int))] = mutable.Set.empty[(User, (Int, Int, Int))]

  def onEvaluation(aEvaluation: ActorRef, bEvaluation: ActorRef): Receive = {
    case EvaluationProcess.CompleteEvaluation(user, q, br, e) =>
      log.info("evaluation from {}: quality={}, breadth={}, engagement={}", user, q, br, e)
      evaluations.add(user -> (q, br, e))
      if (evaluations.size >= 2) {
        database ! MongoStorage.WriteDialog(self.chatId, Set(a, b), txtContext, history, evaluations.toSet)
        self ! PoisonPill
      }

    case EndDialog => log.debug("already engagement")
    case m @ PushMessageToTalk(from, _) =>
      (if (from == a) aEvaluation else if (from == b) bEvaluation else throw new IllegalArgumentException(s"$from not in talk")) forward m

    case _: EvaluateMessage => sender ! akka.actor.Status.Failure(NotAccepted)
  }
}

object Dialog {
  def props(userA: User, userB: User, context: String, gate: ActorRef, database: ActorRef) = Props(new Dialog(userA, userB, context, gate, database))

  case class PushMessageToTalk(from: User, message: String)

  case object StartDialog
  case object EndDialog

  case class EvaluateMessage(messageHash: Int, category: Int)
  case object Ok
  case object NotAccepted extends RuntimeException with NoStackTrace

  implicit class DialogActorRef(ref: ActorRef) {
    val chatId: Int = ref.hashCode()
  }
}
