package org.pavlovai

import akka.actor.{Actor, ActorLogging, Props}
import info.mukel.telegrambot4s.models.Update

/**
  * @author vadim
  * @since 04.07.17
  */
class HumanMessageHandler extends Actor with ActorLogging {
  override def receive: Receive = {
    case u: Update =>
      log.info(u.toString)
  }

}
