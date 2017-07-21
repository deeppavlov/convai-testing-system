package org.pavlovai.dialog

import java.time.{Clock, Instant}

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props, Stash}
import org.pavlovai.communication._

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._
import scala.util.Try
import scala.util.control.NoStackTrace

/**
  * @author vadim
  * @since 06.07.17
  */
class Dialog(a: User, b: User, txtContext: String, gate: ActorRef, database: ActorRef, clck: Clock) extends Actor with ActorLogging with Stash {
  import Dialog._

  private val timeout = Try(Duration.fromNanos(context.system.settings.config.getDuration("talk.talk_timeout").toNanos)).getOrElse(1.minutes)
  private val maxLen = Try(context.system.settings.config.getInt("talk.talk_length_max")).getOrElse(1000)

  private implicit val ec = context.dispatcher
  context.system.scheduler.scheduleOnce(timeout) { self ! EndDialog }

  private val history: mutable.LinkedHashMap[Int, (User, String, Int)] = mutable.LinkedHashMap.empty[Int, (User, String, Int)]

  override def receive: Receive = {
    case StartDialog =>
      def firstMessageFor(user: User, text: String): Endpoint.MessageFromDialog = user match {
        case u: Human => Endpoint.SystemNotificationToUser(u, text)
        case u: Bot => Endpoint.ChatMessageToUser(u, "/start " + text, self.chatId, Instant.now(clck).getNano)
      }

      gate ! firstMessageFor(a, txtContext)
      gate ! firstMessageFor(b, txtContext)

    case PushMessageToTalk(from, text) =>
      @tailrec
      def genId: Int = {
        val id = Instant.now(clck).getNano
        if (history.contains(id)) genId
        else id
      }

      val oppanent = if (from == a) b else if (from == b) a else throw new IllegalArgumentException(s"$from not in talk")
      val id = genId
      gate ! Endpoint.ChatMessageToUser(oppanent, text, self.chatId, id)
      //TODO: use hash as id may leads to message lost!
      history.put(id, (from, text, 0))
      if (history.size > maxLen) self ! EndDialog

    case EndDialog =>
      val e1 = context.actorOf(EvaluationProcess.props(a, self, gate), name=s"evaluation-process-${self.chatId}-${a.id}")
      e1 ! EvaluationProcess.StartEvaluation
      val e2 = context.actorOf(EvaluationProcess.props(b, self, gate), name=s"evaluation-process-${self.chatId}-${b.id}")
      e2 ! EvaluationProcess.StartEvaluation
      context.become(onEvaluation(e1, e2))
      unstashAll()

    case EvaluateMessage(messageId, category) =>
      history.get(messageId).fold {
        sender ! akka.actor.Status.Failure(BadEvaluation)
        log.info("message {} not present in history", messageId)
      } { case (from, text, _) =>
        history.update(messageId, (from, text, category))
        sender ! Ok
        log.info("rated message {} with {}", messageId, category)
      }

    case EvaluationProcess.CompleteEvaluation(user, q, br, e) => stash()
  }

  private val evaluations: mutable.Set[(User, (Int, Int, Int))] = mutable.Set.empty[(User, (Int, Int, Int))]

  def onEvaluation(aEvaluation: ActorRef, bEvaluation: ActorRef): Receive = {
    case EvaluationProcess.CompleteEvaluation(user, q, br, e) =>
      log.info("evaluation from {}: quality={}, breadth={}, engagement={}", user, q, br, e)
      evaluations.add(user -> (q, br, e))
      if (evaluations.size >= 2) {
        database ! MongoStorage.WriteDialog(self.chatId, Set(a, b), txtContext, history.values.toList, evaluations.toSet)
        self ! PoisonPill
      }

    case EndDialog => log.debug("already engagement")
    case m @ PushMessageToTalk(from, _) =>
      (if (from == a) aEvaluation else if (from == b) bEvaluation else throw new IllegalArgumentException(s"$from not in talk")) forward m

    case EvaluateMessage(messageId, category) =>
      history.get(messageId).fold {
        sender ! akka.actor.Status.Failure(BadEvaluation)
        log.info("message {} not present in history", messageId)
      } { case (from, text, _) =>
        history.update(messageId, (from, text, category))
        sender ! Ok
        log.info("rated message {} with {}", messageId, category)
      }

    case m => log.debug("message ignored {}", m)
  }
}

object Dialog {
  def props(userA: User, userB: User, context: String, gate: ActorRef, database: ActorRef, clck: Clock) = Props(new Dialog(userA, userB, context, gate, database, clck))

  case class PushMessageToTalk(from: User, message: String)

  case object StartDialog
  case object EndDialog

  case class EvaluateMessage(messageId: Int, category: Int)
  case object Ok
  case object BadEvaluation extends RuntimeException with NoStackTrace

  implicit class DialogActorRef(ref: ActorRef) {
    val chatId: Int = ref.hashCode()
  }
}
