package org.pavlovai.dialog

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import org.pavlovai.communication.{Bot, Endpoint, HumanChat, User}

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

  context.system.scheduler.scheduleOnce(timeout) { self ! Timeout }

  private var messagesCount: Int = 0

  gate ! firstMessageFor(a, txt)
  gate ! firstMessageFor(b, txt)

  override def receive: Receive = {
    case PushMessageToTalk(from, text) =>
      val oppanent = if (from == a) b else if (from == b) a else throw new IllegalArgumentException(s"$from not in talk")
      gate ! Endpoint.DeliverMessageToUser(oppanent, text)
      messagesCount += 1
      if (messagesCount > maxLen) self ! PoisonPill

    case Timeout => self ! PoisonPill
  }
}

object Dialog {
  def props(userA: User, userB: User, context: String, gate: ActorRef) = Props(new Dialog(userA, userB, context, gate))

  case class PushMessageToTalk(from: User, message: String)

  private def firstMessageFor(user: User, text: String): Endpoint.DeliverMessageToUser = user match {
    case u: HumanChat => Endpoint.DeliverMessageToUser(u, text)
    case u: Bot => Endpoint.DeliverMessageToUser(u, "/start " + text)
  }

  private case object Timeout
}
