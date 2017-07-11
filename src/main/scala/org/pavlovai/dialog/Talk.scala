package org.pavlovai.dialog

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import org.pavlovai.user.{Gate, User, UserRepository}

/**
  * @author vadim
  * @since 06.07.17
  */
class Talk(a: User, b: User, context: String, gate: ActorRef) extends Actor with ActorLogging {
  gate ! Gate.DeliverMessageToUser(a, context)
  gate ! Gate.DeliverMessageToUser(b, context)

  override def receive: Receive = {
    case Gate.PushMessageToTalk(from, text) =>
      val oppanent = if (from == a) b else if (from == b) a else throw new IllegalArgumentException(s"$from not in talk")
      gate ! Gate.DeliverMessageToUser(oppanent, text)
  }
}

object Talk {
  def props(userA: User, userB: User, context: String, gate: ActorRef) = Props(new Talk(userA, userB, context, gate))
}
