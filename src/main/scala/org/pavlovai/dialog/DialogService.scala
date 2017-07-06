package org.pavlovai.dialog

import akka.actor.{Actor, ActorLogging, ActorRef, Props}

/**
  * @author vadim
  * @since 06.07.17
  */
class DialogService(telegramUserRepo: ActorRef, roboUserRepo: ActorRef) extends Actor with ActorLogging {
  override def receive: Receive = ???
}

object DialogService {
  def props(telegramUserRepo: ActorRef, roboUserRepo: ActorRef) = Props(new DialogService(telegramUserRepo, roboUserRepo))
}