package org.pavlovai.communication.telegram

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Stash}
import info.mukel.telegrambot4s.api._
import info.mukel.telegrambot4s.methods.{ParseMode, SendMessage}
import info.mukel.telegrambot4s.models._
import org.pavlovai.dialog.{Dialog, DialogFather}
import org.pavlovai.communication.{Endpoint, HumanChat}

import scala.collection.mutable

/**
  * @author vadim
  * @since 04.07.17
  */
class TelegramEndpoint(daddy: ActorRef) extends Actor with ActorLogging with Stash {
  import TelegramEndpoint._

  override def receive: Receive = unititialized

  private val unititialized: Receive = {
    case SetGateway(gate) =>
      context.become(initialized(gate))
      unstashAll()

    case m => stash()
  }

  private def initialized(request: RequestHandler): Receive = {
    case SetGateway(g) => context.become(initialized(g))

    case Command(chat, "/start") =>
      request(helpMessage(chat.id))

    case Command(chat, "/help") =>
      request(helpMessage(chat.id))

    case Command(Chat(id, ChatType.Private, _, username, _, _, _, _, _, _), "/begin") =>
      daddy ! DialogFather.UserAvailable(HumanChat(id, username.getOrElse("unknown")))

    case Command(Chat(id, ChatType.Private, _, username, _, _, _, _, _, _), "/end") =>
      daddy ! DialogFather.UserUnavailable(HumanChat(id, username.getOrElse("unknown")))

    case Command(chat, _) =>
      request(SendMessage(Left(chat.id), "Messages of this type aren't supported \uD83D\uDE1E"))

    case Update(num, Some(message), _, _, _, _, _, _, _, _) if isInDialog(HumanChat(message.chat.id, message.chat.username.getOrElse("unknown"))) =>
      val user = HumanChat(message.chat.id, message.chat.username.getOrElse("unknown"))
      activeUsers.get(user).foreach { talk =>
        message.text.foreach(talk ! Dialog.PushMessageToTalk(user, _))
      }

    case Update(num, Some(message), _, _, _, _, _, _, _, _)  if isNotInDialog(HumanChat(message.chat.id, message.chat.username.getOrElse("unknown"))) =>
      request(helpMessage(message.chat.id))

    case Update(num, Some(message), _, _, _, _, _, _, _, _) => request(helpMessage(message.chat.id))


    case Endpoint.AddTargetTalkForUserWithChat(user: HumanChat, talk: ActorRef) => activeUsers += user -> talk
    case Endpoint.RemoveTargetTalkForUserWithChat(user: HumanChat) => activeUsers -= user

    case Endpoint.DeliverMessageToUser(HumanChat(id, _), text) =>
      request(SendMessage(Left(id), text, Some(ParseMode.Markdown)))
  }

  private val activeUsers = mutable.Map[HumanChat, ActorRef]()

  private def isInDialog(user: HumanChat) = activeUsers.keySet.contains(user)
  private def isNotInDialog(user: HumanChat) = !isInDialog(user)

  private def helpMessage(chatId: Long) = SendMessage(Left(chatId),
    """
      |*Help message*
      |
      |Use:
      |
      |- /begin for start talk
      |- /end for end talk
      |- /help for help
      |
      |[link](http://vkurselife.com/wp-content/uploads/2016/05/b5789b.jpg)
    """.stripMargin, Some(ParseMode.Markdown))
}

object TelegramEndpoint {
  def props(talkConstructor: ActorRef): Props = Props(new TelegramEndpoint(talkConstructor))

  private case object Command {
    def unapply(message: Update): Option[(Chat, String)] = {
      if (message.message.exists(_.text.exists(_.startsWith("/")))) Some((message.message.get.chat, message.message.get.text.get)) else None
    }
  }

  case class SetGateway(gate: RequestHandler)
}
