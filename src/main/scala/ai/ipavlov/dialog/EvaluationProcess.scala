package ai.ipavlov.dialog

import java.time.Instant

import ai.ipavlov.Implicits
import ai.ipavlov.communication.user.{Bot, Human, UserSummary}
import ai.ipavlov.communication.Endpoint
import akka.actor.{Actor, ActorLogging, ActorRef, Props}

import scala.util.Try

/**
  * @author vadim
  * @since 13.07.17
  */
class EvaluationProcess(user: UserSummary, dialog: ActorRef, gate: ActorRef) extends Actor with ActorLogging with Implicits {
  import Dialog._
  import EvaluationProcess._

  var q = 0
  var b = 0
  var e = 0

  override def receive: Receive = {
    case StartEvaluation =>
      user match {
        case user: Human =>
          context.become(dialogEvaluationQuality(user))
          gate ! Endpoint.AskEvaluationFromHuman(user, s"Chat is finished, please evaluate the overall quality")
        case u: Bot =>
          dialog ! CompleteEvaluation(u ,0, 0, 0)
          gate ! Endpoint.ChatMessageToUser(u, "/end", dialog.chatId, Instant.now().getNano.toString)
      }
  }

  private def dialogEvaluationQuality(u: Human): Receive = {
    case PushMessageToTalk(_, rate) if Try(rate.toInt).filter(r => (r > 0) && (r <= 5)).isSuccess =>
      log.debug(s"the $u rated the quality by $rate")
      q = rate.toInt
      context.become(dialogEvaluationBreadth(u))
      gate ! Endpoint.AskEvaluationFromHuman(u, s"Please evaluate the breadth")
    case PushMessageToTalk(from: Human, _) => gate ! Endpoint.AskEvaluationFromHuman(from, """Please use integers from 1 to 5""")
    case m: PushMessageToTalk => log.debug("ignore message {}", m)

  }

  private def dialogEvaluationBreadth(u: Human): Receive = {
      case PushMessageToTalk(_, rate) if Try(rate.toInt).filter(rate => (rate > 0) && (rate <= 5)).isSuccess =>
        log.debug(s"the $u rated the breadth by $rate")
        b = rate.toInt
        context.become(dialogEvaluationEngagement(u))
        gate ! Endpoint.AskEvaluationFromHuman(u, s"Please evaluate the engagement")
      case PushMessageToTalk(from: Human, _) => gate ! Endpoint.AskEvaluationFromHuman(from, """Please use integers from 1 to 5""")
      case m: PushMessageToTalk => log.debug("ignore message {}", m)
  }

  private def dialogEvaluationEngagement(u: Human): Receive = {
    case PushMessageToTalk(_, rate) if Try(rate.toInt).filter(rate => (rate > 0) && (rate <= 5)).isSuccess =>
      log.debug(s"the $u rated the engagement by $rate")
      e = rate.toInt
      gate ! Endpoint.EndHumanDialog(u, "Thank you! It was great! Please choose /begin to continue evaluation.")
      dialog ! CompleteEvaluation(u ,q, b, e)
    case PushMessageToTalk(from: Human, _) => gate ! Endpoint.AskEvaluationFromHuman(from, """Please use integers from 1 to 5""")
    case m: PushMessageToTalk => log.debug("ignore message {}", m)
  }
}

object EvaluationProcess {
  def props(user: UserSummary, dialog: ActorRef, gate: ActorRef) = Props(new EvaluationProcess(user, dialog, gate))

  case class CompleteEvaluation(user: UserSummary, quality: Int, breadth: Int, engagement: Int)

  case object StartEvaluation
}
