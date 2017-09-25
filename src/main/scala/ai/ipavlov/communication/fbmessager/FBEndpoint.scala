package ai.ipavlov.communication.fbmessager

import java.time.Instant

import ai.ipavlov.Messages
import ai.ipavlov.communication.{Endpoint, FbChat}
import ai.ipavlov.dialog.{Dialog, DialogFather}
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Stash}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.LazyLogging

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Failure

/**
  * @author vadim
  * @since 22.09.17
  */
class FBEndpoint(daddy: ActorRef, storage: ActorRef, pageAccessEndpoint: String) extends Actor with ActorLogging with Stash {
  import FBEndpoint._

  private val fBService = FBService(self)

  override def receive: Receive = {
    case Endpoint.ActivateTalkForUser(user: FbChat, talk: ActorRef) =>
      activeUsers += user -> Some(talk)

    case Endpoint.FinishTalkForUser(user: FbChat, _) =>
      activeUsers -= user

    case Endpoint.SystemNotificationToUser(FbChat(id), text) =>
      fBService.notify(text, id, pageAccessEndpoint)(context.dispatcher, context.system, ActorMaterializer())

    case Endpoint.ChatMessageToUser(FbChat(id), text, _, mesId) =>
      fBService.chatItem(text, id, pageAccessEndpoint)(context.dispatcher, context.system, ActorMaterializer())

    case Endpoint.EndHumanDialog(FbChat(id), text) =>
      fBService.prompt(text, id, pageAccessEndpoint)(context.dispatcher, context.system, ActorMaterializer())

    case Endpoint.AskEvaluationFromHuman(FbChat(id), text) =>
      fBService.notify(text, id, pageAccessEndpoint)(context.dispatcher, context.system, ActorMaterializer())


    case Message(FbChat(id), "/help") =>
      fBService.notify(Messages.helpMessage, id, pageAccessEndpoint)(context.dispatcher, context.system, ActorMaterializer())

    case Message(chat: FbChat, "/begin") if isNotInDialog(chat.chatId) =>
      daddy ! DialogFather.UserAvailable(chat, 1)
      activeUsers += chat -> None

    case Message(chat: FbChat, "/end") if isInDialog(chat.chatId) =>
      daddy ! DialogFather.UserLeave(chat)
      if (activeUsers.get(chat).flatten.isEmpty) {
        activeUsers.remove(chat)
        //FBService.notify(Messages.exit, chat.chatId, pageAccessEndpoint)(context.dispatcher, context.system, ActorMaterializer())
      }

    case Message(FbChat(id), command) if (isNotInDialog(id) && command == "/end") || (isInDialog(id) && command == "/begin") =>
      fBService.notify(Messages.helpMessage, id, pageAccessEndpoint)(context.dispatcher, context.system, ActorMaterializer())

    case Message(chat: FbChat, text) if isInDialog(chat.chatId) =>
      activeUsers.get(chat).foreach {
        case Some(talk) => talk ! Dialog.PushMessageToTalk(chat, text)
        case _ =>
      }

    case Message(FbChat(id), _) =>
      fBService.notify(Messages.notSupported, id, pageAccessEndpoint)(context.dispatcher, context.system, ActorMaterializer())
  }

  private val activeUsers = mutable.Map[FbChat, Option[ActorRef]]()

  private def isInDialog(chatId: Long) = activeUsers.contains(FbChat(chatId))
  private def isNotInDialog(chatId: Long) = !isInDialog(chatId)

  private case class FBService(ca: ActorRef) extends LazyLogging {
    private val responseUri = "https://graph.facebook.com/v2.6/me/messages"

    private def splitText(txt: String): Seq[String] = {
      txt.split(" ").foldLeft(List("")) { case (acc, c) =>
        if (acc.head.length <= 600) (acc.head + " " + c) :: acc.tail
        else c :: acc
      }.reverse
    }

    private def now = Instant.now().toEpochMilli

    def chatItem(text: String, receiverId: Long, pageAccessToken: String)(implicit ec: ExecutionContext, system: ActorSystem,
                                                                          materializer :ActorMaterializer) {
      import spray.json._

      splitText(text).foreach { txt =>
        logger.info("send chat message " + txt)

        val fbMessage = FBMessageEventOut(
          recipient = FBRecipient(receiverId.toString),
          message = FBMessage(
            text = Some(txt),
            metadata = Some("DEVELOPER_DEFINED_METADATA"),
            seq = Some(now),
            quick_replies = Some(List(
              FBQuickReply("\uD83D\uDC4D", "like"),
              FBQuickReply("\uD83D\uDC4E", "ulike")
            ))
          )
        ).toJson.toString()

        Http().singleRequest(HttpRequest(
          HttpMethods.POST,
          uri = s"$responseUri?access_token=$pageAccessToken",
          entity = HttpEntity(ContentTypes.`application/json`, fbMessage))
        ).andThen {
          case Failure(err) => logger.info("can't send response", err)
        }
      }
    }

    def notify(text: String, receiverId: Long, pageAccessToken: String)(implicit ec: ExecutionContext, system: ActorSystem,
                                                                        materializer :ActorMaterializer) {
      import spray.json._

      splitText("(system msg): " + text).foreach { txt =>
        logger.info("send chat message " + txt)

        val fbMessage = FBMessageEventOut(
          recipient = FBRecipient(receiverId.toString),
          message = FBMessage(
            text = Some(txt),
            seq = Some(now),
            metadata = Some("DEVELOPER_DEFINED_METADATA")
          )
        ).toJson.toString()

        Http().singleRequest(HttpRequest(
          HttpMethods.POST,
          uri = s"$responseUri?access_token=$pageAccessToken",
          entity = HttpEntity(ContentTypes.`application/json`, fbMessage))
        ).andThen {
          case Failure(err) => logger.info("can't send response", err)
        }
      }
    }

    def prompt(text: String, receiverId: Long, pageAccessToken: String)(implicit ec: ExecutionContext, system: ActorSystem,
                                                                        materializer :ActorMaterializer) {
      import spray.json._

      splitText("(system msg): " + text).foreach { txt =>
        logger.info("send chat message " + txt)

        val fbMessage = FBMessageEventOut(
          recipient = FBRecipient(receiverId.toString),
          message = FBMessage(
            text = Some(txt),
            seq = Some(now),
            metadata = Some("DEVELOPER_DEFINED_METADATA"),
            attachment = Some(FBAttachment("template", FBButtonsPayload("ololo?", List(
              FBButton("postback", "/begin", "/begin")))))
          )
        ).toJson.toString()

        Http().singleRequest(HttpRequest(
          HttpMethods.POST,
          uri = s"$responseUri?access_token=$pageAccessToken",
          entity = HttpEntity(ContentTypes.`application/json`, fbMessage))
        ).andThen {
          case Failure(err) => logger.info("can't send response", err)
        }
      }
    }
  }
}

object FBEndpoint {
  def props(daddy: ActorRef, storage: ActorRef, pageAccessEndpoint: String) = Props(new FBEndpoint(daddy, storage, pageAccessEndpoint))

  case class Command(from: FbChat, text: String)

  case class Message(from: FbChat, text: String)
}
