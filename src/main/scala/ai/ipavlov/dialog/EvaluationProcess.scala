package ai.ipavlov.dialog

import java.time.Instant

import ai.ipavlov.Implicits
import ai.ipavlov.communication.user.{Bot, Human, UserSummary}
import ai.ipavlov.communication.Endpoint
import akka.actor.{Actor, ActorLogging, ActorRef, Props}

import scala.concurrent.duration._
import scala.util.Try

/**
  * @author vadim
  * @since 13.07.17
  */
class EvaluationProcess(user: UserSummary, dialog: ActorRef, gate: ActorRef) extends Actor with ActorLogging with Implicits {
  import Dialog._
  import EvaluationProcess._
  import context.dispatcher

  var q = 0
  var b = 0
  var e = 0

  private val timeout = Try(Duration.fromNanos(context.system.settings.config.getDuration("talk.talk_evaluation_timeout").toNanos)).getOrElse(5.minutes)
  private val canceble = context.system.scheduler.scheduleOnce(timeout) { self ! AbortEvaluation }

  private def abort: Receive = {
    case AbortEvaluation =>
      log.warning(s"the evaluation was skipped")
      user match {
        case u: Human => gate ! Endpoint.EndHumanDialog(u, "The evaluation skipped. Please choose /begin to have another conversation.")
        case _ =>
      }
      dialog ! CompleteEvaluation(user , 0, 0,  0)
  }

  override def receive: Receive = abort.orElse {
    case StartEvaluation =>
      user match {
        case user: Human =>
          context.become(dialogEvaluationQuality(user))
          gate ! Endpoint.AskEvaluationFromHuman(user, s"Chat is finished, please evaluate the overall quality by typing in a number between 1 (bad) andgit ci  5 (excellent)")
        case u: Bot =>
          dialog ! CompleteEvaluation(u ,0, 0, 0)
          //TODO: face is empty becouse bot endpoint ignored face field
          gate ! Endpoint.ShowChatMessageToUser(u, "", "/end", dialog.chatId, Instant.now().getNano.toString)
      }
  }

  private def dialogEvaluationQuality(u: Human): Receive = abort.orElse {
    case PushMessageToTalk(_, rate) if Try(rate.toInt).filter(r => (r > 0) && (r <= 5)).isSuccess =>
      log.debug(s"the $u rated the quality by $rate")
      q = rate.toInt
      context.become(dialogEvaluationBreadth(u))
      gate ! Endpoint.AskEvaluationFromHuman(u, s"Please evaluate the breadth")
    case PushMessageToTalk(from: Human, _) => gate ! Endpoint.AskEvaluationFromHuman(from, """Please use integers from 1 to 5""")
    case m: PushMessageToTalk => log.debug("ignore message {}", m)

  }

  private def dialogEvaluationBreadth(u: Human): Receive = abort.orElse {
      case PushMessageToTalk(_, rate) if Try(rate.toInt).filter(rate => (rate > 0) && (rate <= 5)).isSuccess =>
        log.debug(s"the $u rated the breadth by $rate")
        b = rate.toInt
        context.become(dialogEvaluationEngagement(u))
        gate ! Endpoint.AskEvaluationFromHuman(u, s"Please evaluate the engagement")
      case PushMessageToTalk(from: Human, _) => gate ! Endpoint.AskEvaluationFromHuman(from, """Please use integers from 1 to 5""")
      case m: PushMessageToTalk => log.debug("ignore message {}", m)
  }

  private def dialogEvaluationEngagement(u: Human): Receive = abort.orElse {
    case PushMessageToTalk(_, rate) if Try(rate.toInt).filter(rate => (rate > 0) && (rate <= 5)).isSuccess =>
      log.debug(s"the $u rated the engagement by $rate")
      e = rate.toInt
      gate ! Endpoint.EndHumanDialog(u, """Thank you! It was great! Please choose /begin to have another conversation.""")
      dialog ! CompleteEvaluation(u ,q, b, e)
    case PushMessageToTalk(from: Human, _) => gate ! Endpoint.AskEvaluationFromHuman(from, """Please use integers from 1 to 5""")
    case m: PushMessageToTalk => log.debug("ignore message {}", m)
  }
}

object EvaluationProcess {
  def props(user: UserSummary, dialog: ActorRef, gate: ActorRef) = Props(new EvaluationProcess(user, dialog, gate))

  case class CompleteEvaluation(user: UserSummary, quality: Int, breadth: Int, engagement: Int)

  case object StartEvaluation

  case object AbortEvaluation
}
