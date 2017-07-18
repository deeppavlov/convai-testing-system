package org.pavlovai.communication.telegram

import java.util.Base64

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Stash}
import info.mukel.telegrambot4s.api._
import info.mukel.telegrambot4s.methods.{ParseMode, SendMessage}
import info.mukel.telegrambot4s.models._
import org.pavlovai.communication.Endpoint.ChancelTestDialog
import org.pavlovai.communication.{Endpoint, TelegramChat}
import org.pavlovai.dialog.{Dialog, DialogFather}

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
        """.stripMargin, Some(ParseMode.Markdown), replyMarkup = Some(ReplyKeyboardRemove())))

    case Command(chat, "/help") =>
      telegramCall(helpMessage(chat.id))

    case Command(Chat(id, ChatType.Private, _, username, _, _, _, _, _, _), cmd) if isNotInDialog(id) && cmd.startsWith("/test") =>
      daddy ! DialogFather.CreateTestDialogWithBot(TelegramChat(id), cmd.substring("/test".length).trim)
      activeUsers += TelegramChat(id) -> None

    case Command(Chat(id, ChatType.Private, _, username, _, _, _, _, _, _), "/begin") if isNotInDialog(id) =>
      daddy ! DialogFather.UserAvailable(TelegramChat(id))
      activeUsers += TelegramChat(id) -> None

    case Command(Chat(id, ChatType.Private, _, username, _, _, _, _, _, _), "/begin") if isInDialog(id) =>
      telegramCall(SendMessage(Left(id), "Messages of this type aren't supported \uD83D\uDE1E"))

    case Command(Chat(chatId, ChatType.Private, _, username, _, _, _, _, _, _), "/end") if isInDialog(chatId) =>
      daddy ! DialogFather.UserLeave(TelegramChat(chatId))
      if (activeUsers.get(TelegramChat(chatId)).isEmpty) activeUsers.remove(TelegramChat(chatId))

    case Command(Chat(id, ChatType.Private, _, username, _, _, _, _, _, _), "/end") if isNotInDialog(id) =>
      telegramCall(SendMessage(Left(id), "Messages of this type aren't supported \uD83D\uDE1E"))

    case Command(chat, _) =>
      telegramCall(SendMessage(Left(chat.id), "Messages of this type aren't supported \uD83D\uDE1E"))

    case Update(num, Some(message), _, _, _, _, _, _, _, _) if isInDialog(message.chat.id) =>
      val user = TelegramChat(message.chat.id)
      activeUsers.get(user).foreach {
        case Some(talk) => message.text.foreach(talk ! Dialog.PushMessageToTalk(user, _))
        case _ =>
      }

    case Update(num, Some(message), _, _, _, _, _, _, _, _)  if isNotInDialog(message.chat.id) => telegramCall(helpMessage(message.chat.id))

    case Update(num, Some(message), _, _, _, _, _, _, _, _) => telegramCall(helpMessage(message.chat.id))

    case ChancelTestDialog(user: TelegramChat, cause) =>
      activeUsers -= user
      telegramCall(SendMessage(Left(user.chatId), cause))

    case Endpoint.ActivateTalkForUser(user: TelegramChat, talk: ActorRef) => activeUsers += user -> Some(talk)
    case Endpoint.FinishTalkForUser(user: TelegramChat, _) =>
      activeUsers -= user

    case Endpoint.SystemNotificationToUser(TelegramChat(id), text) =>
      telegramCall(SendMessage(Left(id), text, Some(ParseMode.Markdown), replyMarkup = Some(ReplyKeyboardRemove())))

    case Endpoint.ChatMessageToUser(TelegramChat(id), text, dialogId) =>
      //TODO use messageId instead hash
      def encodeCallback(dialogId: Int, message: String, value: Option[String]) = Base64.getEncoder.encode((dialogId, message.hashCode, value.getOrElse("unknown")).toString().getBytes()).mkString

      telegramCall(SendMessage(Left(id), text, Some(ParseMode.Markdown), replyMarkup = Some(
        InlineKeyboardMarkup(Seq(Seq(
          InlineKeyboardButton.callbackData("- 0 -",  encodeCallback(dialogId, text, None)),
          InlineKeyboardButton.callbackData("bot", encodeCallback(dialogId, text, Some("bot"))),
          InlineKeyboardButton.callbackData("human", encodeCallback(dialogId, text, Some("human")))
        ))
        ))))

    case Endpoint.AskEvaluationFromHuman(h, text) =>
      telegramCall(
        SendMessage(
          Left(h.chatId),
          text,
          Some(ParseMode.Markdown),
          replyMarkup = Some(ReplyKeyboardMarkup(resizeKeyboard = Some(true), oneTimeKeyboard = Some(true), keyboard = Seq(
            Seq( KeyboardButton("1"), KeyboardButton("2"), KeyboardButton("3"), KeyboardButton("4"), KeyboardButton("5") ),
            Seq( KeyboardButton("6"), KeyboardButton("7"), KeyboardButton("8"), KeyboardButton("9"), KeyboardButton("10") )
          )))
        )
      )
  }

  private val activeUsers = mutable.Map[TelegramChat, Option[ActorRef]]()

  private def isInDialog(chatId: Long) = activeUsers.contains(TelegramChat(chatId))
  private def isNotInDialog(chatId: Long) = !isInDialog(chatId)

  private def helpMessage(chatId: Long) = SendMessage(Left(chatId),
    """
      |Use:
      |
      |- /begin for start talk
      |- /end for end talk
      |- /help for help
      |
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
