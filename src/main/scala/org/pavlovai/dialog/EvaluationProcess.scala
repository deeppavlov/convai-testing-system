package org.pavlovai.dialog

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import org.pavlovai.communication._

import scala.util.Try

/**
  * @author vadim
  * @since 13.07.17
  */
class EvaluationProcess(user: User, dialog: ActorRef, gate: ActorRef) extends Actor with ActorLogging {
  import Dialog._
  import EvaluationProcess._

  override def receive: Receive = {
    case StartEvaluation =>
      user match {
        case user: Human =>
          context.become(dialogEvaluationQuality(user))
          gate ! Endpoint.AskEvaluationFromHuman(user, s"Chat is finished, please evaluate the quality")
        case u: Bot =>
          dialog ! CompleteEvaluation(u ,0, 0, 0)
          gate ! Endpoint.DeliverMessageToUser(u, "/end", Some(dialog.chatId))
          self ! PoisonPill
      }
  }

  private def dialogEvaluationQuality(u: Human): Receive = {
    case PushMessageToTalk(_, rate) if Try(rate.toInt).filter(r => (r > 0) && (r <= 10)).isSuccess =>
      log.info(s"the $u rated the quality by $rate")
      context.become(dialogEvaluationBreadth(u))
      gate ! Endpoint.AskEvaluationFromHuman(u, s"Please evaluate the breadth")
    case PushMessageToTalk(from: Human, _) => gate ! Endpoint.AskEvaluationFromHuman(from, """Please use integers in diapason \[1, 10\]""")
    case m: PushMessageToTalk => log.debug("ignore message {}", m)

  }

  private def dialogEvaluationBreadth(u: Human): Receive = {
      case PushMessageToTalk(_, rate) if Try(rate.toInt).filter(rate => (rate > 0) && (rate <= 10)).isSuccess =>
        log.info(s"the $u rated the breadth by $rate")
        context.become(dialogEvaluationEngagement(u))
        gate ! Endpoint.AskEvaluationFromHuman(u, s"Please evaluate the engagement")
      case PushMessageToTalk(from: Human, _) => gate ! Endpoint.AskEvaluationFromHuman(from, """Please use integers in diapason \[1, 10\]""")
      case m: PushMessageToTalk => log.debug("ignore message {}", m)
  }

  private def dialogEvaluationEngagement(u: Human): Receive = {
    case PushMessageToTalk(_, rate) if Try(rate.toInt).filter(rate => (rate > 0) && (rate <= 10)).isSuccess =>
      log.info(s"the $u rated the engagement by $rate")
      self ! PoisonPill
      gate ! Endpoint.DeliverMessageToUser(user, "Thank you!", Some(dialog.chatId))
    case PushMessageToTalk(from: Human, _) => gate ! Endpoint.AskEvaluationFromHuman(from, """Please use integers in diapason \[1, 10\]""")
    case m: PushMessageToTalk => log.debug("ignore message {}", m)
  }
}

object EvaluationProcess {
  def props(user: User, dialog: ActorRef, gate: ActorRef) = Props(new EvaluationProcess(user, dialog, gate))

  case class CompleteEvaluation(user: User, quality: Int, breadth: Int, engagement: Int)

  case object StartEvaluation
}
