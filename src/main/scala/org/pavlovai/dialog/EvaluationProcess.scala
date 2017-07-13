package org.pavlovai.dialog

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import org.pavlovai.communication._
import org.pavlovai.dialog.Dialog.{EndDialog, PushMessageToTalk}

import scala.util.Try

/**
  * @author vadim
  * @since 13.07.17
  */
class EvaluationProcess(user: User, dialog: ActorRef, gate: ActorRef) extends Actor with ActorLogging {
  import Dialog._
  import EvaluationProcess._

  override def preStart(): Unit = {
    super.preStart()

    user match {
      case _: Human =>
      case u: Bot =>
        dialog ! CompleteEvaluation(u ,0, 0, 0)
        gate ! Endpoint.DeliverMessageToUser(u, "/end", dialog.chatId)
        self ! PoisonPill
    }
  }

  override def receive: Receive = dialogEvaluationQuality

  private def dialogEvaluationQuality: Receive = {
    def lastMessageFor(user: User): Unit = user match {
      case u: TelegramChat => gate ! Endpoint.AskEvaluationFromHuman(u, s"Chat is finished, please evaluate the quality", self.chatId)
      case u: Bot => gate ! Endpoint.DeliverMessageToUser(u, "/end", self.chatId)
    }



    {
      case PushMessageToTalk(user, rate) if Try(rate.toInt).filter(r => (r > 0) && (r <= 10)).isSuccess =>
        log.info(s"the $user rated the quality by $rate")
        context.become(dialogEvaluationBreadth)
      case PushMessageToTalk(from: Human, _) => gate ! Endpoint.AskEvaluationFromHuman(from, """Please use integers in diapason \[1, 10\]""", dialog.chatId)
      case m: PushMessageToTalk => log.debug("ignore message {}", m)
    }
  }

  private def dialogEvaluationBreadth: Receive = {
    def lastMessageFor(user: User): Unit = user match {
      case u: TelegramChat => gate ! Endpoint.AskEvaluationFromHuman(u, s"Please evaluate the breadth", self.chatId)
      case u: Bot =>
    }

    lastMessageFor(a)
    lastMessageFor(b)

    {
      case EndDialog(u) => log.debug("already finishing")
      case PushMessageToTalk(user, rate) if Try(rate.toInt).filter(rate => (rate > 0) && (rate <= 10)).isSuccess =>
        log.info(s"the $user rated the breadth by $rate")
        context.become(dialogEvaluationEngagement)
      case PushMessageToTalk(from: Human, _) => gate ! Endpoint.AskEvaluationFromHuman(from, """Please use integers in diapason \[1, 10\]""", self.chatId)
      case m: PushMessageToTalk => log.debug("ignore message {}", m)
    }
  }

  private def dialogEvaluationEngagement: Receive = {
    def lastMessageFor(user: User): Unit = user match {
      case u: TelegramChat => gate ! Endpoint.AskEvaluationFromHuman(u, s"Please evaluate the engagement", self.chatId)
      case u: Bot =>
    }

    lastMessageFor(a)
    lastMessageFor(b)

    {
      case EndDialog(u) => log.debug("already engagement")
      case PushMessageToTalk(user, rate) if Try(rate.toInt).filter(rate => (rate > 0) && (rate <= 10)).isSuccess =>
        log.info(s"the $user rated the engagement by $rate")
        gate ! Endpoint.DeliverMessageToUser(user, "Thank you!", self.chatId)
        self ! PoisonPill
      case PushMessageToTalk(from: Human, _) => gate ! Endpoint.AskEvaluationFromHuman(from, """Please use integers in diapason \[1, 10\]""", self.chatId)
      case m: PushMessageToTalk => log.debug("ignore message {}", m)
    }
  }
}

object EvaluationProcess {
  def props(user: User, dialog: ActorRef, gate: ActorRef) = Props(new EvaluationProcess(user, dialog, gate))

  case class CompleteEvaluation(user: User, quality: Int, breadth: Int, engagement: Int)
}
