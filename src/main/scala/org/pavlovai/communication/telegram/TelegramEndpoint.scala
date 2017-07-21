package org.pavlovai.communication.telegram

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Stash}
import akka.util.Timeout
import buildinfo.BuildInfo
import info.mukel.telegrambot4s.api._
import info.mukel.telegrambot4s.methods.{AnswerCallbackQuery, EditMessageReplyMarkup, ParseMode, SendMessage}
import info.mukel.telegrambot4s.models._
import org.pavlovai.communication.Endpoint.ChancelTestDialog
import org.pavlovai.communication.{Endpoint, TelegramChat}
import org.pavlovai.dialog.{Dialog, DialogFather}

import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.Try
import scala.util.control.NonFatal

/**
  * @author vadim
  * @since 04.07.17
  */
class TelegramEndpoint(daddy: ActorRef) extends Actor with ActorLogging with Stash with akka.pattern.AskSupport {
  import TelegramEndpoint._
  import context.dispatcher
  private implicit val timeout: Timeout = 5.seconds

  override def receive: Receive = unititialized

  private val unititialized: Receive = {
    case SetGateway(gate) =>
      context.become(initialized(gate))
      unstashAll()

    case m => stash()
  }

  private def initialized(telegramCall: RequestHandler): Receive = {
    case SetGateway(g) => context.become(initialized(g))

    case Command(chat, "/start") if isNotInDialog(chat.id, chat.username) =>
      telegramCall(SendMessage(Left(chat.id),
        """
          |Welcome!
          |You’re going to participate in The Conversational Intelligence Challenge as a volunteer.
          |Your conversations with a peer will be recorded for further use. By starting a chat you give permission for your anonymised conversation data to be released publicly under Apache License Version 2.0. Please use command /help for instruction.
          |
          |We are glad to announce our sponsors: Facebook and Flint Capital[.](https://raw.githubusercontent.com/deepmipt/nips_router_bot/master/src/main/resources/sponsors_480.png)
        """.stripMargin, Some(ParseMode.Markdown), replyMarkup = Some(ReplyKeyboardMarkup(resizeKeyboard = Some(true), oneTimeKeyboard = Some(true), keyboard = Seq(
          Seq( KeyboardButton("/begin") ),
          Seq( KeyboardButton("/help") )
        )))
      ))

    case Command(chat, "/help") =>
      telegramCall(helpMessage(chat.id))

    case Command(Chat(id, ChatType.Private, _, username, _, _, _, _, _, _), cmd) if isNotInDialog(id, username) && cmd.startsWith("/test") =>
      daddy ! DialogFather.CreateTestDialogWithBot(TelegramChat(id, username), cmd.substring("/test".length).trim)
      activeUsers += TelegramChat(id, username) -> None

    case Command(Chat(id, ChatType.Private, _, username, _, _, _, _, _, _), "/begin") if isNotInDialog(id, username) && username.isDefined =>
      daddy ! DialogFather.UserAvailable(TelegramChat(id, username))
      activeUsers += TelegramChat(id, username) -> None

    case Command(Chat(id, ChatType.Private, _, username, _, _, _, _, _, _), "/begin") if isNotInDialog(id, username) && username.isEmpty =>
      telegramCall(SendMessage(Left(id), "`(system msg):` please set username in your telegram account", Some(ParseMode.Markdown)))

    case Command(Chat(id, ChatType.Private, _, username, _, _, _, _, _, _), "/version") if isNotInDialog(id, username) =>
      telegramCall(SendMessage(Left(id), "`(system msg):` " + BuildInfo.version, Some(ParseMode.Markdown)))

    case Command(Chat(id, ChatType.Private, _, username, _, _, _, _, _, _), "/begin") if isInDialog(id, username) =>
      telegramCall(SendMessage(Left(id), "Messages of this type aren't supported \uD83D\uDE1E"))

    case Command(Chat(chatId, ChatType.Private, _, username, _, _, _, _, _, _), "/end") if isInDialog(chatId, username) =>
      daddy ! DialogFather.UserLeave(TelegramChat(chatId, username))
      if (activeUsers.get(TelegramChat(chatId, username)).flatten.isEmpty) {
        activeUsers.remove(TelegramChat(chatId, username))
        telegramCall(SendMessage(Left(chatId),"""`(system msg):` exit""", Some(ParseMode.Markdown), replyMarkup = Some(ReplyKeyboardMarkup(resizeKeyboard = Some(true), oneTimeKeyboard = Some(true), keyboard = Seq(
            Seq( KeyboardButton("/begin") )
          )))
        ))
      }

    case Command(Chat(id, ChatType.Private, _, username, _, _, _, _, _, _), "/end") if isNotInDialog(id, username) =>
      telegramCall(SendMessage(Left(id), "Messages of this type aren't supported \uD83D\uDE1E"))

    case Command(chat, _) =>
      telegramCall(SendMessage(Left(chat.id), "Messages of this type aren't supported \uD83D\uDE1E"))

    case Update(num, Some(message), _, _, _, _, _, None, _, _) if isInDialog(message.chat.id, message.chat.username) =>
      val user = TelegramChat(message.chat.id, message.chat.username)
      activeUsers.get(user).foreach {
        case Some(talk) => message.text.foreach(talk ! Dialog.PushMessageToTalk(user, _))
        case _ =>
      }

    case  Update(num , _, _, _, _, _, _, Some(CallbackQuery(cdId, user, Some(responseToMessage), _, _, Some(data),None)), None, None) =>
      log.debug("received m: {}, d: {}", responseToMessage.text.map(_.hashCode), data)
      (data.split(",").toList, responseToMessage.text, responseToMessage.chat.id) match {
        case (messageId :: value :: Nil, Some(text), chatId) if Try(messageId.toInt).isSuccess =>
          val category = if (value == "unlike") 1 else if (value == "like") 2 else 0
          activeUsers.get(TelegramChat(chatId, user.username)).map {
            case Some(dialog) =>
              (dialog ? Dialog.EvaluateMessage(messageId.toInt, category)).map {
                case Dialog.Ok =>
                  telegramCall(AnswerCallbackQuery(cdId, None, Some(false), None, None))
                  val (labelLike, labelUnlike) = ("\uD83D\uDC4D" + (if (value == "like") "\u2605" else ""), "\uD83D\uDC4E"  + (if (value == "unlike") "\u2605" else ""))
                  telegramCall(EditMessageReplyMarkup(Some(Left(responseToMessage.chat.id)), Some(responseToMessage.messageId), replyMarkup = Some(InlineKeyboardMarkup(Seq(Seq(
                    InlineKeyboardButton.callbackData(labelLike, encodeCbData(messageId.toInt, "like")),
                    InlineKeyboardButton.callbackData(labelUnlike, encodeCbData(messageId.toInt, "unlike"))
                  )))
                  )))
              }.recover {
                case Dialog.BadEvaluation =>
                  log.warning("bad evaluation")
                  telegramCall(AnswerCallbackQuery(cdId, Some("Bad request"), Some(true), None, None))
                case NonFatal(e) =>
                  log.error("error on evaluation item: {}", e)
                  telegramCall(AnswerCallbackQuery(cdId, Some("Internal server error"), Some(true), None, None))
              }
            case _ =>
              log.debug("trying rate item in finished dialog")
              telegramCall(AnswerCallbackQuery(cdId, Some("Sorry, dialog is finished"), Some(true), None, None))
          }


        case _ =>
          log.warning("bad evaluation")
          telegramCall(AnswerCallbackQuery(cdId, Some("Bad request"), Some(true), None, None))
      }

    case Update(num, Some(message), _, _, _, _, _, None, _, _) if isNotInDialog(message.chat.id, message.chat.username) => telegramCall(helpMessage(message.chat.id))

    case ChancelTestDialog(user: TelegramChat, cause) =>
      activeUsers -= user
      telegramCall(SendMessage(Left(user.chatId), cause))

    case Endpoint.ActivateTalkForUser(user: TelegramChat, talk: ActorRef) => activeUsers += user -> Some(talk)
    case Endpoint.FinishTalkForUser(user: TelegramChat, _) =>
      activeUsers -= user

    case Endpoint.SystemNotificationToUser(TelegramChat(id, _), text) =>
      telegramCall(SendMessage(Left(id), "`(system msg):` " + text, Some(ParseMode.Markdown), replyMarkup = Some(ReplyKeyboardRemove())))

    case Endpoint.EndHumanDialog(TelegramChat(id, _), text) =>
      telegramCall(SendMessage(Left(id), "`(system msg):` " + text, Some(ParseMode.Markdown),
        replyMarkup = Some(ReplyKeyboardMarkup(resizeKeyboard = Some(true), oneTimeKeyboard = Some(true), keyboard = Seq(
          Seq( KeyboardButton("/begin") )
        )))
      ))

    case Endpoint.ChatMessageToUser(TelegramChat(id, _), text, _, mesId) =>
      telegramCall(SendMessage(Left(id), "`(partner msg):` " + text, Some(ParseMode.Markdown), replyMarkup = Some(
        InlineKeyboardMarkup(Seq(Seq(
          InlineKeyboardButton.callbackData("\uD83D\uDC4D", encodeCbData(mesId, "like")),
          InlineKeyboardButton.callbackData("\uD83D\uDC4E", encodeCbData(mesId, "unlike"))
        ))
        ))))

    case Endpoint.AskEvaluationFromHuman(h, text) =>
      telegramCall(
        SendMessage(
          Left(h.chatId),
          "`(system msg):` " + text,
          Some(ParseMode.Markdown),
          replyMarkup = Some(ReplyKeyboardMarkup(resizeKeyboard = Some(true), oneTimeKeyboard = Some(true), keyboard = Seq(
            Seq( KeyboardButton("1"), KeyboardButton("2"), KeyboardButton("3"), KeyboardButton("4"), KeyboardButton("5") )
          )))
        )
      )
  }

  private val activeUsers = mutable.Map[TelegramChat, Option[ActorRef]]()

  private def isInDialog(chatId: Long, username: Option[String]) = activeUsers.contains(TelegramChat(chatId, username))
  private def isNotInDialog(chatId: Long, username: Option[String]) = !isInDialog(chatId, username)

  private def helpMessage(chatId: Long) = SendMessage(Left(chatId),
    """
      |1. To start a dialog type or choose a ```/start``` command.
      |2. You will be connected to a peer or, if no peer is available at the moment, you’ll receive the message from @ConvaiBot 'Please wait for you peer.'
      |3. Peer might be a bot or another human evaluator.
      |4. After you were connected with your peer you will receive a starting message - a passage or two from a Wikipedia article.
      |5. Your task is to discuss the content of a presented passage with the peer and score her/his replies.
      |6. Please score every utterance of your peer with a 'thumb UP' button if you like it, and 'thumb DOWN' button in the opposite case.
      |To finish the conversation type or choose a command ```/end```.
      |7. When the conversation is finished, you will receive a request from @ConvaiBot to score the overall quality of the dialog along three dimensions:
      | - quality - how much are you satisfied with the whole conversation?
      | - breadth - in your opinion was a topic discussed thoroughly or just from one side?
      | - engagement - was it interesting to participate in this conversation?
      |8. If your peer ends the dialog before you, you will also receive a scoring request from @ConvaiBot.
      |9. Your conversations with a peer will be recorded for further use. By starting a chat you give permission for your anonymised conversation data to be released publicly under [Apache License Version 2.0](https://www.apache.org/licenses/LICENSE-2.0).
    """.stripMargin, Some(ParseMode.Markdown), replyMarkup = Some(ReplyKeyboardRemove()))

  private def encodeCbData(messageId: Int, text: String) = s"$messageId,$text"
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
