package ai.ipavlov.communication.telegram

import ai.ipavlov.communication.user.{Client, Messages}
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
    case Client.ShowChatMessage(receiverAddress, face, messageId, text) =>
      telegramCall(SendMessage(Left(receiverAddress.toLong), face + xml.Utility.escape(text), Some(ParseMode.HTML), replyMarkup = Some(
        InlineKeyboardMarkup(Seq(Seq(
          InlineKeyboardButton.callbackData("\uD83D\uDC4D", encodeCbData(messageId, "like")),
          InlineKeyboardButton.callbackData("\uD83D\uDC4E", encodeCbData(messageId, "dislike"))
        ))
        ))))

    case Client.ShowContext(receiverAddress, text) =>
      telegramCall(SendMessage(Left(receiverAddress.toLong), text, Some(ParseMode.Markdown), replyMarkup =
        Some(ReplyKeyboardMarkup(resizeKeyboard = Some(true), oneTimeKeyboard = Some(true), keyboard = Seq(
          Seq( KeyboardButton("/end") )
        )))
      ))

    case Client.ShowSystemNotification(receiverAddress, text) =>
      telegramCall(SendMessage(Left(receiverAddress.toLong), Messages.robotFace + text, Some(ParseMode.Markdown), replyMarkup = Some(ReplyKeyboardRemove())))

    case Client.ShowEvaluationMessage(receiverAddress, text) =>
      telegramCall(
        SendMessage(
          Left(receiverAddress.toLong),
          Messages.robotFace + text,
          Some(ParseMode.Markdown),
          replyMarkup = Some(ReplyKeyboardMarkup(resizeKeyboard = Some(true), oneTimeKeyboard = Some(true), keyboard = Seq(
            Seq( KeyboardButton("1"), KeyboardButton("2"), KeyboardButton("3"), KeyboardButton("4"), KeyboardButton("5") )
          )))
        )
      )

    case Client.ShowLastNotificationInDialog(receiverId, text) =>
      telegramCall(SendMessage(Left(receiverId.toLong), Messages.robotFace + text, Some(ParseMode.Markdown),
        replyMarkup = Some(ReplyKeyboardMarkup(resizeKeyboard = Some(true), oneTimeKeyboard = Some(true), keyboard = Seq(
          Seq( KeyboardButton("/begin") )
        )))
      ))

    case Client.ShowHelpMessage(address, isShort) =>
      val text = if (isShort)
        """This is ConvAI bot.
          |Press "begin" button to start a conversation or "help" for help.
        """.stripMargin
      else
        """
          |1. Please set your Username in Settings menu.
          |    - MacOS & iOS:
          |    Gear ("Settings") button to bottom left, after that "Username";
          |    - Windows & Linux & Android:
          |    Menu button left top, "Settings" and "Username" field.
          |2. To start a dialog type or choose a /begin command .
          |3. You will be connected to a peer or, if no peer is available at the moment, you’ll receive the message from @ConvaiBot `Please wait for you partner.`.
          |4. Peer might be a bot or another human evaluator.
          |5. After you were connected with your peer you will receive a starting message - a passage or two from a Wikipedia article.
          |6. Your task is to discuss the content of a presented passage with the peer and score her/his replies.
          |7. Please score every utterance of your peer with a ‘thumb UP’ button if you like it, and ‘thumb DOWN’ button in the opposite case.
          |8. To finish the conversation type or choose a command /end.
          |9. When the conversation is finished, you will receive a request from @ConvaiBot to score the overall quality of the dialog along three dimensions:
          |    - quality - how much are you satisfied with the whole conversation?
          |    - breadth - in your opinion was a topic discussed thoroughly or just from one side?
          |    - engagement - was it interesting to participate in this conversation?
          |10. If your peer ends the dialog before you, you will also receive a scoring request from @ConvaiBot.
          |11. Your conversations with a peer will be recorded for further use. By starting a chat you give permission for your anonymised conversation data to be released publicly under [Apache License Version 2.0](https://www.apache.org/licenses/LICENSE-2.0).
        """.stripMargin

      telegramCall(SendMessage(Left(address.toLong), Messages.robotFace + text, Some(ParseMode.Markdown),
        replyMarkup = Some(ReplyKeyboardMarkup(resizeKeyboard = Some(true), oneTimeKeyboard = Some(true), keyboard = Seq(
          Seq( KeyboardButton("/begin"), KeyboardButton("/help") )
        )))
      ))
  }

  private def encodeCbData(messageId: String, text: String) = s"$messageId,$text"
}

object TelegramClient {
  def props(telegramCall: RequestHandler) = Props(new TelegramClient(telegramCall))
}