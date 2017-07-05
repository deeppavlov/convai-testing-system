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
class HumanMessageHandler(gate: TelegramBot with Commands with ActorBroker) extends Actor with ActorLogging {
  import HumanMessageHandler._
  import info.mukel.telegrambot4s.Implicits._

  override def receive: Receive = {
    case Initialize =>
      gate.onCommand("/help") { implicit msg =>
        gate.withArgs { args =>
          gate.reply("TODO")
        }
      }

    case Update(_, Some(message), _, _, _, _, _, _, _, _) =>
      gate.request(SendMessage(message.source, "olololo"))
  }
}

object HumanMessageHandler {
  case object Initialize
}
