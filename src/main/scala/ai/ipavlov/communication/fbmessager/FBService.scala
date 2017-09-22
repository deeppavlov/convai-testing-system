package ai.ipavlov.communication.fbmessager

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Failure

/**
  * @author vadim
  * @since 18.09.17
  */
object FBService extends LazyLogging  {

  //TODO
  private val responseUri = "https://graph.facebook.com/v2.6/me/messages"

  def verifyToken(token: String, mode: String, challenge: String, originalToken: String)
                 (implicit ec: ExecutionContext):
  (StatusCode, List[HttpHeader], Option[Either[String, String]]) = {

    if(mode == "subscribe" && token == originalToken){
      logger.info(s"Verify webhook token: $token, mode $mode")
      (StatusCodes.OK, List.empty[HttpHeader], Some(Left(challenge)))
    }
    else {
      logger.error(s"Invalid webhook token: $token, mode $mode")
      (StatusCodes.Forbidden, List.empty[HttpHeader], None)
    }
  }

  def handleMessage(fbObject: FBPObject, pageAccessToken: String)
                   (implicit ec: ExecutionContext, system: ActorSystem,
                    materializer :ActorMaterializer):
  (StatusCode, List[HttpHeader], Option[Either[String, String]]) = {
    import spray.json._

    logger.info(s"Receive fbObject: $fbObject")
    fbObject.entry.foreach { entry =>
        entry.messaging.foreach { me =>
          val senderId = me.sender.id
          val message = me.message
          message.text match {
            case Some(text) =>
              val fbMessage = FBMessageEventOut(
                recipient = FBRecipient(senderId),
                message = FBMessage(
                  text = None,//Some(s"Scala messenger bot: $text"),
                  metadata = Some("DEVELOPER_DEFINED_METADATA"),
                  quick_replies = Some(List(FBQuickReply("ololo?","stub"))),
                  attachment = Some(FBAttachment("template", FBButtonsPayload("ololo?", List(
                    FBButton("postback", "1", "?1"), FBButton("postback", "2", "?2"), FBButton("postback", "3", "?3")))))
                )
              ).toJson.toString()

              val responseFuture: Future[HttpResponse] =
                Http().singleRequest(HttpRequest(
                  HttpMethods.POST,
                  uri = s"$responseUri?access_token=$pageAccessToken",
                  entity = HttpEntity(ContentTypes.`application/json`, fbMessage))
                ).andThen {
                  case Failure(err) => logger.info("can't send response", err)
                }
            case None =>
              logger.info("Receive image")
              Future.successful(())
          }
        }
    }
    (StatusCodes.OK, List.empty[HttpHeader], None)
  }
}
