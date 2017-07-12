package org.pavlovai.dialog

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import org.pavlovai.communication.{Endpoint, User}

/**
  * @author vadim
  * @since 06.07.17
  */
class Dialog(a: User, b: User, context: String, gate: ActorRef) extends Actor with ActorLogging {
  import Dialog._

  gate ! Endpoint.DeliverMessageToUser(a, context)
  gate ! Endpoint.DeliverMessageToUser(b, context)

  override def receive: Receive = {
    case PushMessageToTalk(from, text) =>
      val oppanent = if (from == a) b else if (from == b) a else throw new IllegalArgumentException(s"$from not in talk")
      gate ! Endpoint.DeliverMessageToUser(oppanent, text)
  }
}

object Dialog {
  def props(userA: User, userB: User, context: String, gate: ActorRef) = Props(new Dialog(userA, userB, context, gate))

  case class PushMessageToTalk(from: User, message: String)
}
