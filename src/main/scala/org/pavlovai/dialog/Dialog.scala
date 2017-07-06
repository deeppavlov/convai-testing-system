package org.pavlovai.dialog

import akka.actor.{Actor, ActorLogging, ActorRef, Props}

/**
  * @author vadim
  * @since 06.07.17
  */
class Dialog(a: User, b: User, context: String) extends Actor with ActorLogging {
  override def receive: Receive = ???
}

object Dialog {
  case class MessageTo(user: User, text: String)

  def props(userA: User, userB: User, context: String) = Props(new Dialog(userA, userB, context))
}
