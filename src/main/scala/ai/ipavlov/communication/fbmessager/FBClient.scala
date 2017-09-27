package ai.ipavlov.communication.fbmessager

import ai.ipavlov.communication.user.Client
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest}
import akka.stream.ActorMaterializer

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Failure
import scala.concurrent.duration._

/**
  * @author vadim
  * @since 26.09.17
  */
class FBClient(daddy: ActorRef, storage: ActorRef, pageAccessToken: String) extends Actor with ActorLogging {
  import context.dispatcher

  private implicit val system: ActorSystem = context.system
  private implicit val mat: ActorMaterializer = ActorMaterializer()

  override def receive: Receive = {
    case Client.ShowChatMessage(receiverId, messageId, text) =>
      sendMessage(text, receiverId, pageAccessToken, txt => FBMessage(
        text = Some(txt),
        metadata = Some("DEVELOPER_DEFINED_METADATA"),
        quick_replies = Some(List(
          FBQuickReply("\uD83D\uDC4D", "like"),
          FBQuickReply("\uD83D\uDC4E", "ulike")
        ))
      )
      )
    case Client.ShowSystemNotification(receiverId, text) =>
      sendMessage("`(system msg):` " + text, receiverId, pageAccessToken, txt => FBMessage(
        text = Some(txt),
        metadata = Some("DEVELOPER_DEFINED_METADATA")
      )
      )
    case Client.ShowEvaluationMessage(receiverId, text) =>
      sendMessage("`(system msg):` " + text, receiverId, pageAccessToken, txt => FBMessage(
        text = Some(txt),
        metadata = Some("DEVELOPER_DEFINED_METADATA")
      )
      )
    case Client.ShowLastNotificationInDialog(receiverId, text) =>
      sendMessage("`(system msg):` " + text, receiverId, pageAccessToken, txt => FBMessage(
        text = Some(txt),
        metadata = Some("DEVELOPER_DEFINED_METADATA"),
        attachment = Some(FBAttachment("template", FBButtonsPayload("push /begin for start new dialog", List(
          FBButton("postback", "/begin", "/begin")))))
      )
      )
  }

  private def sendMessage(text: String, receiverId: String, pageAccessToken: String, f: (String) => FBMessage): Unit = {

    def splitText(txt: String): Seq[String] = txt.split(" ").foldLeft(List("")) { case (acc, c) =>
      if (acc.head.length <= 600) (acc.head + " " + c) :: acc.tail
      else c :: acc
    }.reverse

    def post(txt: String): Future[Unit] = {
      val responseUri = "https://graph.facebook.com/v2.6/me/messages"

      import spray.json._

      val message = FBMessageEventOut(
        recipient = FBRecipient(receiverId.toString),
        message = f(txt)
      ).toJson

      log.info("!!!!!!!!!!!! " + text)
      Http().singleRequest(HttpRequest(
        HttpMethods.POST,
        uri = s"$responseUri?access_token=$pageAccessToken",
        entity = HttpEntity(ContentTypes.`application/json`, message.toString))
      ).andThen {
        case Failure(err) => log.error("message did not send", err)
      }.map(_ => ())
    }

    splitText(text).foldLeft(Future.successful(())) { case (ft, mes) => ft.flatMap(_ => post(mes)) }
  }
}

object FBClient {
  def props(daddy: ActorRef, storage: ActorRef, pageAccessToken: String) = Props(new FBClient(daddy, storage, pageAccessToken))
}
