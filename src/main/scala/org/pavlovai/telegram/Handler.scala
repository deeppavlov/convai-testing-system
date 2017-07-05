package org.pavlovai.telegram

import akka.actor.{Actor, ActorLogging}
import info.mukel.telegrambot4s.actors.ActorBroker
import info.mukel.telegrambot4s.api._
import info.mukel.telegrambot4s.api.declarative._
import info.mukel.telegrambot4s.methods.{ParseMode, SendMessage}
import info.mukel.telegrambot4s.models._

/**
  * @author vadim
  * @since 04.07.17
  */
class Handler(gate: BotBase) extends Actor with ActorLogging {
  import Handler._
  //import info.mukel.telegrambot4s.Implicits._

  self ! Initialize

  override def receive: Receive = {
    case Initialize =>

    case Command(chatId, "/help") =>
      gate.request(SendMessage(Left(chatId),
        """
          |#TODO
          |
          |- write help
          |- read help
          |- fix help
          |
          |![img](http://vkurselife.com/wp-content/uploads/2016/05/b5789b.jpg)
        """.stripMargin, Some(ParseMode.Markdown)))

    case Update(_, Some(message), _, _, _, _, _, _, _, _) =>
      gate.request(SendMessage(Left(message.source), message.text.toString))
  }
}

object Handler {
  case object Initialize

  private case object Command {
    def unapply(message: Update): Option[(Long, String)] = {
      if (message.message.exists(_.text.exists(_.startsWith("/")))) Some((message.message.get.source, message.message.get.text.get)) else None
    }
  }
}
