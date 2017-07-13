package org.pavlovai.communication.telegram

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Stash}
import info.mukel.telegrambot4s.api._
import info.mukel.telegrambot4s.methods.ParseMode.ParseMode
import info.mukel.telegrambot4s.methods.{ParseMode, SendMessage}
import info.mukel.telegrambot4s.models.{ReplyMarkup, _}
import org.pavlovai.dialog.{Dialog, DialogFather}
import org.pavlovai.communication.{Endpoint, TelegramChat}

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

  private def initialized(telegramCall: RequestHandler): Receive = {
    case SetGateway(g) => context.become(initialized(g))

    case Command(chat, "/start") =>
      telegramCall(SendMessage(Left(chat.id),
        """
          |*Welcome!*
          |
          |Use:
          |
          |- /begin for start talk
          |- /end for end talk
          |- /help for help
          |
          |[](http://vkurselife.com/wp-content/uploads/2016/05/b5789b.jpg)
        """.stripMargin, Some(ParseMode.Markdown), replyMarkup = Some(ReplyKeyboardRemove())))

    case Command(chat, "/help") =>
      telegramCall(helpMessage(chat.id))

    case Command(Chat(id, ChatType.Private, _, username, _, _, _, _, _, _), "/begin") if isNotInDialog(id) =>
      daddy ! DialogFather.UserAvailable(TelegramChat(id))

    case Command(Chat(id, ChatType.Private, _, username, _, _, _, _, _, _), "/begin") if isInDialog(id) =>
      telegramCall(SendMessage(Left(id), "Messages of this type aren't supported \uD83D\uDE1E"))

    case Command(Chat(id, ChatType.Private, _, username, _, _, _, _, _, _), "/end") if isInDialog(id) =>
      daddy ! DialogFather.UserLeave(TelegramChat(id))

    case Command(Chat(id, ChatType.Private, _, username, _, _, _, _, _, _), "/end") if isNotInDialog(id) =>
      telegramCall(SendMessage(Left(id), "Messages of this type aren't supported \uD83D\uDE1E"))

    case Command(chat, _) =>
      telegramCall(SendMessage(Left(chat.id), "Messages of this type aren't supported \uD83D\uDE1E"))

    case Update(num, Some(message), _, _, _, _, _, _, _, _) if isInDialog(message.chat.id) =>
      val user = TelegramChat(message.chat.id)
      activeUsers.get(user).foreach { talk =>
        message.text.foreach(talk ! Dialog.PushMessageToTalk(user, _))
      }

    case Update(num, Some(message), _, _, _, _, _, _, _, _)  if isNotInDialog(message.chat.id) =>
      telegramCall(helpMessage(message.chat.id))

    case Update(num, Some(message), _, _, _, _, _, _, _, _) => telegramCall(helpMessage(message.chat.id))


    case Endpoint.ActivateTalkForUser(user: TelegramChat, talk: ActorRef) => activeUsers += user -> talk
    case Endpoint.FinishTalkForUser(user: TelegramChat, _) => activeUsers -= user

    case Endpoint.DeliverMessageToUser(TelegramChat(id), text, _) =>
      telegramCall(SendMessage(Left(id), text, Some(ParseMode.Markdown), replyMarkup = Some(ReplyKeyboardRemove())))

    case Endpoint.AskEvaluationFromHuman(h, text, _) =>
      telegramCall(
        SendMessage(
          Left(h.chatId),
          text,
          Some(ParseMode.Markdown),
          replyMarkup = Some(ReplyKeyboardMarkup(resizeKeyboard = Some(true), oneTimeKeyboard = Some(true), keyboard = Seq(Seq(
            KeyboardButton("1"), KeyboardButton("2"), KeyboardButton("3"), KeyboardButton("4"), KeyboardButton("5"), KeyboardButton("6"), KeyboardButton("7"), KeyboardButton("8"), KeyboardButton("9"), KeyboardButton("10")
          ))))
        )
      )
  }

  private val activeUsers = mutable.Map[TelegramChat, ActorRef]()

  private def isInDialog(chatId: Long) = activeUsers.keySet.contains(TelegramChat(chatId))
  private def isNotInDialog(chatId: Long) = !isInDialog(chatId)

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
    """.stripMargin, Some(ParseMode.Markdown), replyMarkup = Some(ReplyKeyboardRemove()))
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
