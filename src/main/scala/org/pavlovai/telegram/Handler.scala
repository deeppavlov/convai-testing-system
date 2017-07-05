package org.pavlovai.telegram

import akka.actor.{Actor, ActorLogging}
import info.mukel.telegrambot4s.actors.ActorBroker
import info.mukel.telegrambot4s.api._
import info.mukel.telegrambot4s.api.declarative._
import info.mukel.telegrambot4s.methods.SendMessage
import info.mukel.telegrambot4s.models._

/**
  * @author vadim
  * @since 04.07.17
  */
class Handler(gate: BotBase) extends Actor with ActorLogging {
  import Handler._
  import info.mukel.telegrambot4s.Implicits._

  self ! Initialize

  override def receive: Receive = {
    case Initialize =>

    case Update(_, Some(message), _, _, _, _, _, _, _, _) if message.text.getOrElse("null").head == '/' =>
      gate.request(SendMessage(message.source, "command"))

    case Update(_, Some(message), _, _, _, _, _, _, _, _) =>
      gate.request(SendMessage(message.source, message.text.toString))
  }
}

object Handler {
  case object Initialize
}
