package org.pavlovai.communication.telegram

import java.util.Base64

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Stash}
import info.mukel.telegrambot4s.api._
import info.mukel.telegrambot4s.methods.{EditMessageReplyMarkup, ParseMode, SendMessage}
import info.mukel.telegrambot4s.models.{Message, _}
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

    case Update(num, Some(message), _, _, _, _, _, None, _, _) if isInDialog(message.chat.id) =>
      val user = TelegramChat(message.chat.id)
      activeUsers.get(user).foreach {
        case Some(talk) => message.text.foreach(talk ! Dialog.PushMessageToTalk(user, _))
        case _ =>
      }

    case  Update(num , _, _, _, _, _, _, Some(CallbackQuery(cdId, user, Some(responseToMessage), inlineMessageId, _, Some(data),None)), None,None) =>
      log.info("received m: {}, d: {}", responseToMessage, data)
      //telegramCall(EditMessageReplyMarkup(Some(Left(m.chat.id))))

    case Update(num, Some(message), _, _, _, _, _, None, _, _) if isNotInDialog(message.chat.id) => telegramCall(helpMessage(message.chat.id))

    //case Update(num, Some(_), _, _, _, _, _, _, _, _) =>

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
      def encodeCallback(dialogId: Int, message: String, value: Option[String]) = dialogId + "," + value.getOrElse("unknown")

      telegramCall(SendMessage(Left(id), text, Some(ParseMode.Markdown), replyMarkup = Some(
        InlineKeyboardMarkup(Seq(Seq(
          InlineKeyboardButton.callbackData("\uD83D\uDC4D", encodeCallback(dialogId, text, Some("bot"))),
          InlineKeyboardButton.callbackData("\uD83D\uDC4E", encodeCallback(dialogId, text, Some("human")))
        ))
        ))))

    case Endpoint.AskEvaluationFromHuman(h, text) =>
      telegramCall(
        SendMessage(
          Left(h.chatId),
          text,
          Some(ParseMode.Markdown),
          replyMarkup = Some(ReplyKeyboardMarkup(resizeKeyboard = Some(true), oneTimeKeyboard = Some(true), keyboard = Seq(
            Seq( KeyboardButton("1"), KeyboardButton("2"), KeyboardButton("3") ),
            Seq( KeyboardButton("4"), KeyboardButton("5") )
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
