package org.pavlovai

import akka.actor.{Actor, ActorLogging, Props}
import info.mukel.telegrambot4s.api._
import info.mukel.telegrambot4s.api.declarative._
import info.mukel.telegrambot4s.methods.{EditMessageReplyMarkup, SendMessage}
import info.mukel.telegrambot4s.models._
import info.mukel.telegrambot4s.Implicits._

/**
  * @author vadim
  * @since 04.07.17
  */
class HumanMessageHandler(request: RequestHandler) extends Actor with ActorLogging {
  override def receive: Receive = {
    case Update(_, Some(message), _, _, _, _, _, _, _, _) =>
      request(SendMessage(message.source, "olololo"))
  }
}
