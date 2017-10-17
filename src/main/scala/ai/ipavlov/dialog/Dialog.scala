package ai.ipavlov.dialog

import java.time.{Clock, Instant}

import ai.ipavlov.Implicits
import ai.ipavlov.communication.Endpoint
import ai.ipavlov.communication.user.{Bot, Human, UserSummary}
import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}

import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Random, Try}

/**
  * @author vadim
  * @since 06.07.17
  */
class Dialog(a: UserSummary, b: UserSummary, txtContext: String, gate: ActorRef, database: ActorRef, clck: Clock) extends Actor with ActorLogging with Implicits {
  import Dialog._

  private def randomFace = Random.shuffle(List("\uD83D\uDC6E", "\uD83D\uDC70", "\uD83D\uDC71", "\uD83D\uDC72", "\uD83D\uDC73",
    "\uD83D\uDC74", "\uD83D\uDC75", "\uD83D\uDC76", "\uD83D\uDC77", "\uD83D\uDC78", "\uD83D\uDC79", "\uD83D\uDC7A",
    "\uD83D\uDC7B", "\uD83D\uDC7C", "\uD83D\uDC7D", "\uD83D\uDC7F", "\uD83D\uDC80", "\uD83D\uDC81", "\uD83D\uDC82")).head + " "

  private val faceA = randomFace
  private val faceB = randomFace

  private val timeout = Try(Duration.fromNanos(context.system.settings.config.getDuration("talk.talk_timeout").toNanos)).getOrElse(1.minutes)
  private val maxLen = Try(context.system.settings.config.getInt("talk.talk_length_max")).getOrElse(1000)

  private implicit val ec: ExecutionContext = context.dispatcher
  private val canceble = context.system.scheduler.scheduleOnce(timeout) { self ! EndDialog }

  override def postStop(): Unit = {
    canceble.cancel()
    super.postStop()
  }

  private val history: mutable.LinkedHashMap[String, (ai.ipavlov.communication.user.UserSummary, String, Int)] = mutable.LinkedHashMap.empty[String, (ai.ipavlov.communication.user.UserSummary, String, Int)]

  log.info("start talk between {} and {}", a, b)

  @tailrec
  private def genId: String = {
    val id = Instant.now(clck).getNano.toString
    if (history.contains(id)) genId
    else id
  }

  override def receive: Receive = {
    case StartDialog =>
      def firstMessageFor(user: UserSummary, face: String, text: String): Endpoint.MessageFromDialog = user match {
        case u: Human => Endpoint.SystemNotificationToUser(u, text)
        case u: Bot => Endpoint.ChatMessageToUser(u, face, "/start " + text, self.chatId, genId)
      }

      gate ! firstMessageFor(a, faceA, txtContext)
      gate ! firstMessageFor(b, faceB, txtContext)

    case PushMessageToTalk(from, text) =>
      val (oppanent, face) = if (from == a) (b, faceB) else if (from == b) (a, faceA) else throw new IllegalArgumentException(s"$from not in talk")
      val id = genId
      gate ! Endpoint.ChatMessageToUser(oppanent, face, text, self.chatId, id)
      //TODO: use hash as id may leads to message lost!
      history.put(id, (from, text, 0))
      if (history.size > maxLen) self ! EndDialog

    case EndDialog =>
      val e1 = context.actorOf(EvaluationProcess.props(a, self, gate), name=s"evaluation-process-${self.chatId}-${a.address}")
      e1 ! EvaluationProcess.StartEvaluation
      val e2 = context.actorOf(EvaluationProcess.props(b, self, gate), name=s"evaluation-process-${self.chatId}-${b.address}")
      e2 ! EvaluationProcess.StartEvaluation
      context.become(onEvaluation(e1, e2))

    case EvaluateMessage(messageId, category) =>
      history.get(messageId).fold {
        log.info("message {} not present in history", messageId)
      } { case (from, text, _) =>
        history.update(messageId, (from, text, category))
        log.info("rated message {} with {}", messageId, category)
      }

  }

  private val evaluations: mutable.Set[(UserSummary, (Int, Int, Int))] = mutable.Set.empty[(UserSummary, (Int, Int, Int))]

  def onEvaluation(aEvaluation: ActorRef, bEvaluation: ActorRef): Receive = {
    case EvaluationProcess.CompleteEvaluation(user, q, br, e) =>
      log.info("evaluation from {}: quality={}, breadth={}, engagement={}", user, q, br, e)
      evaluations.add(user -> (q, br, e))
      user match {
        case user: Human =>
          gate ! Endpoint.FinishTalkForUser(user, self)
          log.debug("human {} unavailable now", user)
        case user: Bot => log.debug("bot {} finished talk", user)
      }
      if (evaluations.size >= 2) {
        database ! MongoStorage.WriteDialog(self.chatId, Set(a, b), txtContext, history.values.toList, evaluations.toSet)
        self ! PoisonPill
      }

    case EndDialog => log.debug("already engagement")
    case m @ PushMessageToTalk(from, _) =>
      (if (from == a) aEvaluation else if (from == b) bEvaluation else throw new IllegalArgumentException(s"$from not in talk")) forward m

    case EvaluateMessage(messageId, category) =>
      history.get(messageId).fold {
        log.warning("message {} not present in history", messageId)
      } { case (from, text, _) =>
        history.update(messageId, (from, text, category))
        log.info("rated message {} with {}", messageId, category)
      }

    case m => log.debug("message ignored {}", m)
  }
}

object Dialog {
  def props(userA: UserSummary, userB: UserSummary, context: String, gate: ActorRef, database: ActorRef, clck: Clock) = Props(new Dialog(userA, userB, context, gate, database, clck))

  case class PushMessageToTalk(from: UserSummary, message: String)

  case object StartDialog
  case object EndDialog

  case class EvaluateMessage(messageId: String, category: Int)
}
