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

    case Command(chat, "/start") =>
      telegramCall(SendMessage(Left(chat.id),
        """
          |Welcome!
          |You’re going to participate in ConvAI Challenge as volunteer. Please take a look at Volunteer’s Instruction: LINK
          |We are glad to announce our sponsors: Facebook and Flint Capital
          |
          |[Facebook](https://deeppavlov.github.io/convai/facebook.png) [Flint capital](https://deeppavlov.github.io/convai/flint.png)
          |
          |Be aware that your conversations with a peer will be recorded for further use. By starting a chat you give permission for your anonymised conversation data to be released publicly under Apache License Version 2.0.
          |
        """.stripMargin, Some(ParseMode.Markdown), replyMarkup = Some(ReplyKeyboardRemove())))

    case Command(chat, "/help") =>
      telegramCall(helpMessage(chat.id))

    case Command(Chat(id, ChatType.Private, _, username, _, _, _, _, _, _), cmd) if isNotInDialog(id) && cmd.startsWith("/test") =>
      daddy ! DialogFather.CreateTestDialogWithBot(TelegramChat(id), cmd.substring("/test".length).trim)
      activeUsers += TelegramChat(id) -> None

    case Command(Chat(id, ChatType.Private, _, username, _, _, _, _, _, _), "/begin") if isNotInDialog(id) =>
      daddy ! DialogFather.UserAvailable(TelegramChat(id))
      activeUsers += TelegramChat(id) -> None

    case Command(Chat(id, ChatType.Private, _, username, _, _, _, _, _, _), "/version") if isNotInDialog(id) =>
      telegramCall(SendMessage(Left(id), "`(system say):` " + BuildInfo.version, Some(ParseMode.Markdown)))

    case Command(Chat(id, ChatType.Private, _, username, _, _, _, _, _, _), "/begin") if isInDialog(id) =>
      telegramCall(SendMessage(Left(id), "Messages of this type aren't supported \uD83D\uDE1E"))

    case Command(Chat(chatId, ChatType.Private, _, username, _, _, _, _, _, _), "/end") if isInDialog(chatId) =>
      daddy ! DialogFather.UserLeave(TelegramChat(chatId))
      if (activeUsers.get(TelegramChat(chatId)).flatten.isEmpty) activeUsers.remove(TelegramChat(chatId))

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

    case  Update(num , _, _, _, _, _, _, Some(CallbackQuery(cdId, user, Some(responseToMessage), _, _, Some(data),None)), None, None) =>
      log.debug("received m: {}, d: {}", responseToMessage.text.map(_.hashCode), data)
      (data.split(",").toList, responseToMessage.text, responseToMessage.chat.id) match {
        case (messageId :: value :: Nil, Some(text), chatId) if Try(messageId.toInt).isSuccess =>
          val category = if (value == "unlike") 1 else if (value == "like") 2 else 0
          activeUsers.get(TelegramChat(chatId)).map {
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

    case Update(num, Some(message), _, _, _, _, _, None, _, _) if isNotInDialog(message.chat.id) => telegramCall(helpMessage(message.chat.id))

    case ChancelTestDialog(user: TelegramChat, cause) =>
      activeUsers -= user
      telegramCall(SendMessage(Left(user.chatId), cause))

    case Endpoint.ActivateTalkForUser(user: TelegramChat, talk: ActorRef) => activeUsers += user -> Some(talk)
    case Endpoint.FinishTalkForUser(user: TelegramChat, _) =>
      activeUsers -= user

    case Endpoint.SystemNotificationToUser(TelegramChat(id), text) =>
      telegramCall(SendMessage(Left(id), "`(system say):` " + text, Some(ParseMode.Markdown), replyMarkup = Some(ReplyKeyboardRemove())))

    case Endpoint.ChatMessageToUser(TelegramChat(id), text, _, mesId) =>
      telegramCall(SendMessage(Left(id), text, Some(ParseMode.Markdown), replyMarkup = Some(
        InlineKeyboardMarkup(Seq(Seq(
          InlineKeyboardButton.callbackData("\uD83D\uDC4D", encodeCbData(mesId, "like")),
          InlineKeyboardButton.callbackData("\uD83D\uDC4E", encodeCbData(mesId, "unlike"))
        ))
        ))))

    case Endpoint.AskEvaluationFromHuman(h, text) =>
      telegramCall(
        SendMessage(
          Left(h.chatId),
          "`(system say):` " + text,
          Some(ParseMode.Markdown),
          replyMarkup = Some(ReplyKeyboardMarkup(resizeKeyboard = Some(true), oneTimeKeyboard = Some(true), keyboard = Seq(
            Seq( KeyboardButton("1"), KeyboardButton("2"), KeyboardButton("3"), KeyboardButton("4"), KeyboardButton("5") )
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
      |- /begin to start talk
      |- /end to end talk
      |- /help to help
      |
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
