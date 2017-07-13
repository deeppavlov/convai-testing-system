package org.pavlovai.dialog

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import org.pavlovai.communication.{Bot, Endpoint, TelegramChat, User}

import scala.concurrent.duration._
import scala.util.{Random, Try}

/**
  * @author vadim
  * @since 06.07.17
  */
class Dialog(a: User, b: User, txt: String, gate: ActorRef) extends Actor with ActorLogging {
  import Dialog._

  private val id = Random.nextInt()

  private val timeout = Try(Duration.fromNanos(context.system.settings.config.getDuration("talk.talk_timeout").toNanos)).getOrElse(1.minutes)
  private val maxLen = Try(context.system.settings.config.getInt("talk.talk_length_max")).getOrElse(1000)

  private implicit val ec = context.dispatcher
  context.system.scheduler.scheduleOnce(timeout) { self ! EndDialog }

  private var messagesCount: Int = 0

  override def receive: Receive = {
    def firstMessageFor(user: User, text: String): Endpoint.MessageFromDialog = user match {
      case u: TelegramChat => Endpoint.AskEvaluationFromHuman(u, "Ololo???", id)//Endpoint.DeliverMessageToUser(u, text, id)
      case u: Bot => Endpoint.DeliverMessageToUser(u, "/start " + text, id)
    }

    gate ! firstMessageFor(a, txt)
    gate ! firstMessageFor(b, txt)

    {
      case PushMessageToTalk(from, text) =>
        val oppanent = if (from == a) b else if (from == b) a else throw new IllegalArgumentException(s"$from not in talk")
        gate ! Endpoint.DeliverMessageToUser(oppanent, text, id)
        messagesCount += 1
        if (messagesCount > maxLen) self ! EndDialog

      case EndDialog => context.become(dialogFinishing)
    }
  }

  private def dialogFinishing: Receive = {
    def lastMessageFor(user: User): Endpoint.MessageFromDialog = user match {
      case u: TelegramChat => Endpoint.AskEvaluationFromHuman(u, "Ololo???", id)
      case u: Bot => Endpoint.DeliverMessageToUser(u, "/end", id)
    }

    gate ! lastMessageFor(a)
    gate ! lastMessageFor(b)

    {
      case EndDialog => log.debug("already finishing")
      case _ => ???
    }
  }
}

object Dialog {
  def props(userA: User, userB: User, context: String, gate: ActorRef) = Props(new Dialog(userA, userB, context, gate))

  case class PushMessageToTalk(from: User, message: String)

  case object EndDialog
}
