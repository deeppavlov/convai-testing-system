package org.pavlovai.telegram

import java.util.Scanner

import akka.actor.{Actor, ActorLogging, ActorRef}
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

  private val availableUsers = mutable.Set[Long]()
  private val activeUserTalks = mutable.Map[Long, ActorRef]()

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

    case Command(Chat(id, ChatType.Private, _, username, _, _, _, _, _, _), "/begin") =>
      log.info("chat {} ready to talk", username.getOrElse("unknown"))
      readyToTalk(id)

    case Command(chat, "/end") =>
      log.info("chat {} leave talk", chat.username.getOrElse("unknown"))
      leave(chat.id)

    case Update(num, Some(message), _, _, _, _, _, _, _, _) if isNotInTalk(message.chat.id) =>
      gate.request(SendMessage(Left(message.source), "Messages of this type aren't supported \uD83D\uDE1E"))
  }

  private def readyToTalk(chat: Long) = {
    availableUsers += chat
    activeUserTalks -= chat
  }

  private def leave(chat: Long) = {
    availableUsers -= chat
    activeUserTalks -= chat
  }

  private def isInTalk(chat: Long) = activeUserTalks.contains(chat)
  private def isNotInTalk(chat: Long) = !isInTalk(chat)
}

object Handler {
  private case object Command {
    def unapply(message: Update): Option[(Chat, String)] = {
      if (message.message.exists(_.text.exists(_.startsWith("/")))) Some((message.message.get.chat, message.message.get.text.get)) else None
    }
  }
}
