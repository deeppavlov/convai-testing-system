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

  private val id = self.hashCode()

  private val timeout = Try(Duration.fromNanos(context.system.settings.config.getDuration("talk.talk_timeout").toNanos)).getOrElse(1.minutes)
  private val maxLen = Try(context.system.settings.config.getInt("talk.talk_length_max")).getOrElse(1000)

  private implicit val ec = context.dispatcher
  context.system.scheduler.scheduleOnce(timeout) { self ! EndDialog(None) }

  private var messagesCount: Int = 0

  override def receive: Receive = {
    def firstMessageFor(user: User, text: String): Endpoint.MessageFromDialog = user match {
      case u: TelegramChat => Endpoint.DeliverMessageToUser(u, text, id)
      case u: Bot => Endpoint.DeliverMessageToUser(u, "/start " + text, id)
    }

    gate ! firstMessageFor(a, txt)
    gate ! firstMessageFor(b, txt)

    {
      case PushMessageToTalk(from, text) =>
        val oppanent = if (from == a) b else if (from == b) a else throw new IllegalArgumentException(s"$from not in talk")
        gate ! Endpoint.DeliverMessageToUser(oppanent, text, id)
        messagesCount += 1
        if (messagesCount > maxLen) self ! EndDialog(None)

      case EndDialog(u) => context.become(dialogEvaluationQuality)
    }
  }

  private def dialogEvaluationQuality: Receive = {
    def lastMessageFor(user: User): Unit = user match {
      case u: TelegramChat => gate ! Endpoint.AskEvaluationFromHuman(u, s"Chat is finished, please evaluate the quality", id)
      case u: Bot => gate ! Endpoint.DeliverMessageToUser(u, "/end", id)
    }

    lastMessageFor(a)
    lastMessageFor(b)

    {
      case EndDialog(u) => log.debug("already finishing")
      case PushMessageToTalk(user, rate) if Try(rate.toInt).filter(r => (r > 0) && (r <= 10)).isSuccess =>
        log.info(s"the $user rated the quality by $rate")
        context.become(dialogEvaluationBreadth)
      case PushMessageToTalk(from: Human, _) => gate ! Endpoint.AskEvaluationFromHuman(from, "Please use integers in diapason [1, 10]", id)
      case m: PushMessageToTalk => log.debug("ignore message {}", m)
    }
  }

  private def dialogEvaluationBreadth: Receive = {
    def lastMessageFor(user: User): Unit = user match {
      case u: TelegramChat => gate ! Endpoint.AskEvaluationFromHuman(u, s"Please evaluate the breadth", id)
      case u: Bot =>
    }

    lastMessageFor(a)
    lastMessageFor(b)

    {
      case EndDialog(u) => log.debug("already finishing")
      case PushMessageToTalk(user, rate) if Try(rate.toInt).filter(rate => (rate > 0) && (rate <= 10)).isSuccess =>
        log.info(s"the $user rated the breadth by $rate")
        context.become(dialogEvaluationEngagement)
      case PushMessageToTalk(from: Human, _) => gate ! Endpoint.AskEvaluationFromHuman(from, "Please use integers in diapason [1, 10]", id)
      case m: PushMessageToTalk => log.debug("ignore message {}", m)
    }
  }

  private def dialogEvaluationEngagement: Receive = {
    def lastMessageFor(user: User): Unit = user match {
      case u: TelegramChat => gate ! Endpoint.AskEvaluationFromHuman(u, s"Please evaluate the engagement", id)
      case u: Bot =>
    }

    lastMessageFor(a)
    lastMessageFor(b)

    {
      case EndDialog(u) => log.debug("already engagement")
      case PushMessageToTalk(user, rate) if Try(rate.toInt).filter(rate => (rate > 0) && (rate <= 10)).isSuccess =>
        log.info(s"the $user rated the engagement by $rate")
        gate ! Endpoint.DeliverMessageToUser(user, "Thank you!", id)
        self ! PoisonPill
      case PushMessageToTalk(from: Human, _) => gate ! Endpoint.AskEvaluationFromHuman(from, "Please use integers in diapason [1, 10]", id)
      case m: PushMessageToTalk => log.debug("ignore message {}", m)
    }
  }
}

object Dialog {
  def props(userA: User, userB: User, context: String, gate: ActorRef) = Props(new Dialog(userA, userB, context, gate))

  case class PushMessageToTalk(from: User, message: String)

  case class EndDialog(sender: Option[User])
}
