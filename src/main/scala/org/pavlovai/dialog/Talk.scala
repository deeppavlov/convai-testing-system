package org.pavlovai.dialog

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import org.pavlovai.User
import org.pavlovai.telegram.TelegramService.MessageTo

/**
  * @author vadim
  * @since 06.07.17
  */
class Talk(a: User, b: User, context: String, gate: ActorRef) extends Actor with ActorLogging {
  import Talk._

  override def receive: Receive = {
    case MessageFrom(user, text) =>
      val oppanent = if (user == a) b else if (user == b) a else throw new IllegalArgumentException(s"$user not in talk")
      gate ! MessageTo(oppanent, text)
  }
}

object Talk {
  case class MessageFrom(user: User, text: String)

  def props(userA: User, userB: User, context: String, gate: ActorRef) = Props(new Talk(userA, userB, context, gate))
}
