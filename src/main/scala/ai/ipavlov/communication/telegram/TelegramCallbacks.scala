package ai.ipavlov.communication.telegram

import ai.ipavlov.communication.Endpoint
import ai.ipavlov.communication.user.TelegramChat
import akka.actor.{Actor, ActorLogging, ActorRef, Props, Stash}
import akka.util.Timeout
import info.mukel.telegrambot4s.api._
import info.mukel.telegrambot4s.methods.{AnswerCallbackQuery, EditMessageReplyMarkup, ParseMode, SendMessage}
import info.mukel.telegrambot4s.models._

import scala.concurrent.duration._
import scala.util.Try

/**
  * @author vadim
  * @since 04.07.17
  */
class TelegramCallbacks(endpoint: ActorRef, telegramCall: RequestHandler) extends Actor with ActorLogging with Stash with akka.pattern.AskSupport {
  import TelegramCallbacks._
  private implicit val timeout: Timeout = 5.seconds

  private def encodeCbData(messageId: String, text: String) = s"$messageId,$text"

  override val receive: Receive = {
    case Command(chat, "/start") =>
      telegramCall(SendMessage(Left(chat.id),
        """
          |Welcome!
          |You’re going to participate in The Conversational Intelligence Challenge as a volunteer.
          |Your conversations with a peer will be recorded for further use. By starting a chat you give permission for your anonymised conversation data to be released publicly under Apache License Version 2.0. Please use command /help for instruction[.](https://raw.githubusercontent.com/deepmipt/nips_router_bot/master/src/main/resources/sponsors_480.png)
        """.stripMargin, Some(ParseMode.Markdown), replyMarkup = Some(ReplyKeyboardMarkup(resizeKeyboard = Some(true), oneTimeKeyboard = Some(true), keyboard = Seq(
          Seq( KeyboardButton("/begin") ),
          Seq( KeyboardButton("/help") )
        )))
      ))

    case Update(num , _, _, _, _, _, _, Some(CallbackQuery(cdId, User(uid, firstName, _, Some(username), _), Some(responseToMessage), _, _, Some(data),None)), None, None) =>
      (data.split(",").toList, responseToMessage.chat.id) match {
        case (messageId :: "dislike" :: Nil, chatId) if Try(messageId.toInt).isSuccess =>
          endpoint ! Endpoint.EvaluateFromUser(TelegramChat(chatId.toString, username), messageId, 1)
          telegramCall(AnswerCallbackQuery(cdId, None, Some(false), None, None))
          telegramCall(EditMessageReplyMarkup(Some(Left(responseToMessage.chat.id)), Some(responseToMessage.messageId), replyMarkup = Some(InlineKeyboardMarkup(Seq(Seq(
            InlineKeyboardButton.callbackData("\uD83D\uDC4D", encodeCbData(messageId, "like")),
            InlineKeyboardButton.callbackData("\uD83D\uDC4E\u2605", encodeCbData(messageId, "dislike"))
          )))
          )))
        case (messageId :: "like" :: Nil, chatId) if Try(messageId).isSuccess =>
          endpoint ! Endpoint.EvaluateFromUser(TelegramChat(chatId.toString, username), messageId, 2)
          telegramCall(AnswerCallbackQuery(cdId, None, Some(false), None, None))
          telegramCall(EditMessageReplyMarkup(Some(Left(responseToMessage.chat.id)), Some(responseToMessage.messageId), replyMarkup = Some(InlineKeyboardMarkup(Seq(Seq(
            InlineKeyboardButton.callbackData("\uD83D\uDC4D\u2605", encodeCbData(messageId, "like")),
            InlineKeyboardButton.callbackData("\uD83D\uDC4E", encodeCbData(messageId, "dislike"))
          )))
          )))
      }

    case Update(num, Some(message), _, _, _, _, _, None, _, _) if message.chat.username.isEmpty =>
      telegramCall(SendMessage(Left(message.chat.id), """`(system msg):` Please set your Username in Settings menu.
                                           |    - MacOS & iOS:
                                           |    Gear ("Settings") button to bottom left, after that "Username";
                                           |    - Windows & Linux & Android:
                                           |    Menu button left top, "Settings" and "Username" field.""".stripMargin, Some(ParseMode.Markdown)))

    case Update(num, Some(message), _, _, _, _, _, None, _, _) if message.chat.username.isDefined && message.text.isDefined =>
      endpoint ! Endpoint.MessageFromUser(TelegramChat(message.chat.id.toString, message.chat.username.get), message.text.get)

    case m =>
      log.warning("received unknown message: {}", m)
  }
}

object TelegramCallbacks {
  def props(endpoint: ActorRef, telegramCall: RequestHandler): Props = Props(new TelegramCallbacks(endpoint, telegramCall))

  private case object Command {
    def unapply(message: Update): Option[(Chat, String)] = {
      if (message.message.exists(_.text.exists(_.startsWith("/")))) Some((message.message.get.chat, message.message.get.text.get)) else None
    }
  }

  case class SetGateway(gate: RequestHandler)

}
