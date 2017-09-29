package ai.ipavlov.communication.telegram

import ai.ipavlov.communication.user.Client
import akka.actor.{Actor, ActorLogging, Props, Stash}
import info.mukel.telegrambot4s.api.RequestHandler
import info.mukel.telegrambot4s.methods.{ParseMode, SendMessage}
import info.mukel.telegrambot4s.models._

/**
  * @author vadim
  * @since 29.09.17
  */
class TelegramClient(telegramCall: RequestHandler) extends Actor with ActorLogging with Stash {
  override def receive: Receive = {
    case Client.ShowChatMessage(receiverAddress, messageId, text) =>
      telegramCall(SendMessage(Left(receiverAddress.toLong), "<pre>(peer msg):</pre>" + xml.Utility.escape(text), Some(ParseMode.HTML), replyMarkup = Some(
        InlineKeyboardMarkup(Seq(Seq(
          InlineKeyboardButton.callbackData("\uD83D\uDC4D", encodeCbData(receiverAddress.toLong, "like")),
          InlineKeyboardButton.callbackData("\uD83D\uDC4E", encodeCbData(receiverAddress.toLong, "dislike"))
        ))
        ))))

    case Client.ShowSystemNotification(receiverAddress, text) =>
      telegramCall(SendMessage(Left(receiverAddress.toLong), "`(system msg):` " + text, Some(ParseMode.Markdown), replyMarkup = Some(ReplyKeyboardRemove())))

    case Client.ShowEvaluationMessage(receiverAddress, text) =>
      telegramCall(
        SendMessage(
          Left(receiverAddress.toLong),
          "`(system msg):` " + text,
          Some(ParseMode.Markdown),
          replyMarkup = Some(ReplyKeyboardMarkup(resizeKeyboard = Some(true), oneTimeKeyboard = Some(true), keyboard = Seq(
            Seq( KeyboardButton("1"), KeyboardButton("2"), KeyboardButton("3"), KeyboardButton("4"), KeyboardButton("5") )
          )))
        )
      )

    case Client.ShowLastNotificationInDialog(receiverId, text) =>
      telegramCall(SendMessage(Left(receiverId.toLong), "`(system msg):` " + text, Some(ParseMode.Markdown),
        replyMarkup = Some(ReplyKeyboardMarkup(resizeKeyboard = Some(true), oneTimeKeyboard = Some(true), keyboard = Seq(
          Seq( KeyboardButton("/begin") )
        )))
      ))
  }

  private def encodeCbData(messageId: Long, text: String) = s"$messageId,$text"
}

object TelegramClient {
  def props(telegramCall: RequestHandler) = Props(new TelegramClient(telegramCall))
}