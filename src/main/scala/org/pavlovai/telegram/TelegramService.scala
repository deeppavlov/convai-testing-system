package org.pavlovai.telegram

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Stash}
import info.mukel.telegrambot4s.api._
import info.mukel.telegrambot4s.methods.{ParseMode, SendMessage}
import info.mukel.telegrambot4s.models._
import org.pavlovai.dialog.Dialog

import scala.collection.mutable

/**
  * @author vadim
  * @since 04.07.17
  */
class TelegramService extends Actor with ActorLogging with Stash {
  import TelegramService._

  override def receive: Receive = unititialized

  private val unititialized: Receive = {
    case SetGateway(gate) =>
      context.become(initialized(gate))
      unstashAll()

    case m => stash()
  }

  private def initialized(request: RequestHandler): Receive = {
    case SetGateway(g) => context.become(initialized(g))

    case Command(chat, "/help") =>
      request(SendMessage(Left(chat.id),
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
      request(SendMessage(Left(id), "*Please wait for your partner.*", Some(ParseMode.Markdown)))

    case Command(chat, "/end") =>
      log.info("chat {} leave talk", chat.username.getOrElse("unknown"))
      leave(chat.id)

    case Command(chat, _) =>
      request(SendMessage(Left(chat.id), "Messages of this type aren't supported \uD83D\uDE1E"))

    case Update(num, Some(message), _, _, _, _, _, _, _, _) if isInDialog(message.chat.id) =>
      activeUsers.find { case (k, _) => k.id == message.chat.id }.foreach { case (user, dialog) =>
        message.text.foreach(dialog ! Dialog.MessageTo(user, _))
      }

    //TODO: make this more beautiful
    case Update(num, Some(message), _, _, _, _, _, _, _, _) if isNotInDialog(message.chat.id) =>
      request(SendMessage(Left(message.chat.id), "*Please wait for your partner.*", Some(ParseMode.Markdown)))

    case ActivateUser(dialog) =>
      if (availableUsers.nonEmpty) {
        val user = org.pavlovai.dialog.User(availableUsers.toVector(scala.util.Random.nextInt(availableUsers.size)))
        availableUsers -= user.id
        activeUsers += user -> dialog
        Some(user)
      } else None

    case DeactivateUser(org.pavlovai.dialog.User(id)) => readyToTalk(id)
  }

  private val availableUsers = mutable.Set[Long]()
  private val activeUsers = mutable.Map[org.pavlovai.dialog.User, ActorRef]()

  private def readyToTalk(chatId: Long) = {
    availableUsers += chatId
    activeUsers.keys.find(_.id == chatId).foreach(activeUsers.remove)
  }

  private def leave(chatId: Long) = {
    availableUsers -= chatId
    activeUsers.keys.find(_.id == chatId).foreach(activeUsers.remove)
  }

  private def isInDialog(chatId: Long) = activeUsers.exists { case (k, _) => k.id == chatId }
  private def isNotInDialog(chat: Long) = !isInDialog(chat)
}

object TelegramService {
  val props: Props = Props[TelegramService]

  private case object Command {
    def unapply(message: Update): Option[(Chat, String)] = {
      if (message.message.exists(_.text.exists(_.startsWith("/")))) Some((message.message.get.chat, message.message.get.text.get)) else None
    }
  }

  case class SetGateway(gate: RequestHandler)

  case class ActivateUser(dialog: ActorRef)
  case class DeactivateUser(user: org.pavlovai.dialog.User)
}
