package org.pavlovai.user

import akka.actor.{Actor, ActorLogging, ActorRef, Props}

/**
  * @author vadim
  * @since 11.07.17
  */
class CumulativeGate(botGate: ActorRef, telegramGate: ActorRef) extends Actor with ActorLogging {
  import Gate._

  override def receive: Receive = {
    case DeliverMessageToUser(Human(to), message) => telegramGate ! message
    case DeliverMessageToUser(Bot(to), message) => botGate ! message
  }
}

object CumulativeGate {
  def props(botGate: ActorRef, telegramGate: ActorRef): Props = Props(new CumulativeGate(botGate, telegramGate))
}