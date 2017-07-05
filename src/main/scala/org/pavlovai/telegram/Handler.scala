package org.pavlovai.telegram

import akka.actor.{Actor, ActorLogging}
import info.mukel.telegrambot4s.api._
import info.mukel.telegrambot4s.methods.{ParseMode, SendMessage}
import info.mukel.telegrambot4s.models._

import scala.collection.mutable

/**
  * @author vadim
  * @since 04.07.17
  */
class Handler(gate: BotBase) extends Actor with ActorLogging {
  import Handler._

  private val avaliableUsers = mutable.Set[Long]()

  override def receive: Receive = {
    case Command(chat, "/help") =>
      gate.request(SendMessage(Left(chat.id),
        """
          |*TODO*
          |
          |- write help
          |- read help
          |- fix help
          |
          |[link](http://vkurselife.com/wp-content/uploads/2016/05/b5789b.jpg)
        """.stripMargin, Some(ParseMode.Markdown)))

    case Command(Chat(id, ChatType.Private, _, _, _, _, _, _, _, _), "/begin") =>
      avaliableUsers += id

    case Command(chat, "/end") =>
      avaliableUsers -= chat.id

    case Update(num, Some(message), _, _, _, _, _, _, _, _) =>
      gate.request(SendMessage(Left(message.source), "Messages of this type aren't supported \uD83D\uDE1E"))
  }
}

object Handler {
  private case object Command {
    def unapply(message: Update): Option[(Chat, String)] = {
      if (message.message.exists(_.text.exists(_.startsWith("/")))) Some((message.message.get.chat, message.message.get.text.get)) else None
    }
  }
}
