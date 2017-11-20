package ai.ipavlov.communication.fbmessenger

import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import ai.ipavlov.communication.Endpoint
import ai.ipavlov.communication.user.FbChat
import akka.actor.{ActorRef, ActorSystem}
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.{HttpHeader, HttpRequest, StatusCode, StatusCodes}
import akka.http.scaladsl.server.{Directive0, Directives}
import akka.stream.{ActorMaterializer, Materializer}
import org.apache.commons.codec.binary.Hex

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.language.postfixOps

/**
  * @author vadim
  * @since 18.09.17
  */
trait RouteSupport extends Directives {

  def verifyPayload(req: HttpRequest, appSecret: String)
                   (implicit materializer: Materializer,
                    ec: ExecutionContext,
                    logger: LoggingAdapter): Directive0 = {

    def isValid(payload: Array[Byte], secret: String, expected: String): Boolean = {
      val secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1")
      val mac = Mac.getInstance("HmacSHA1")
      mac.init(secretKeySpec)
      val result = mac.doFinal(payload)

      val computedHash = Hex.encodeHex(result).mkString
      logger.debug(s"Computed hash: $computedHash")

      val res = computedHash == expected

      if (!res) logger.warning("token verification failed")

      res
    }

    req.headers.find(_.name == "X-Hub-Signature").map(_.value()) match {
      case Some(token) =>
        val payload =
          Await.result(req.entity.toStrict(5 seconds).map(_.data.decodeString("UTF-8")), 5 second)
        logger.debug(s"Receive token $token and payload $payload")
        val elements = token.split("=")
        val method = elements(0)
        val signaturedHash = elements(1)
        if(isValid(payload.getBytes, appSecret, signaturedHash))
          pass
        else {
          logger.error(s"Tokens are different, expected $signaturedHash")
          complete(StatusCodes.Forbidden)
        }

      case None =>
        logger.error(s"X-Hub-Signature is not defined")
        complete(StatusCodes.Forbidden)
    }
  }

  def handleMessage(endpoint: ActorRef, fbObject: FBPObject, pageAccessToken: String)
                           (implicit ec: ExecutionContext, system: ActorSystem,
                            materializer: ActorMaterializer,
                            logger: LoggingAdapter):
  (StatusCode, List[HttpHeader], Option[Either[String, String]]) = {
    logger.debug(s"Receive fbObject: $fbObject")
    fbObject.entry.foreach { entry =>
      (entry.messaging ++ entry.standby).foreach {
        case FBMessageEventIn(sender, recepient, _, Some(FBMessage(id, _, Some(text), _, _, _, _)), None) =>
          endpoint ! Endpoint.MessageFromUser(FbChat(sender.id, sender.id), text)

        case FBMessageEventIn(sender, _, _, None, Some(FBPostback(payload, _))) if payload.startsWith("like") || payload.startsWith("dislike") =>
          payload.split(" ").toList match {
            case "like" :: messageId :: Nil => endpoint ! Endpoint.EvaluateFromUser(FbChat(sender.id, sender.id), messageId, 2)
            case "dislike" :: messageId :: Nil => endpoint ! Endpoint.EvaluateFromUser(FbChat(sender.id, sender.id), messageId, 1)
            case m => logger.warning("bad evaluation message: {}", m)
          }

        case FBMessageEventIn(sender, _, _, None, Some(FBPostback(payload, _))) =>
          endpoint ! Endpoint.MessageFromUser(FbChat(sender.id, sender.id), payload)

        case m => logger.warning("unhandled message {}", m)
      }
    }
    (StatusCodes.OK, List.empty[HttpHeader], None)
  }

  def verifyToken(token: String, mode: String, challenge: String, originalToken: String)
                         (implicit ec: ExecutionContext,
                          logger: LoggingAdapter):
  (StatusCode, List[HttpHeader], Option[Either[String, String]]) = {

    if (mode == "subscribe" && token == originalToken) {
      logger.debug(s"Verify webhook token: $token, mode $mode")
      (StatusCodes.OK, List.empty[HttpHeader], Some(Left(challenge)))
    }
    else {
      logger.error(s"Invalid webhook token: $token, mode $mode")
      (StatusCodes.Forbidden, List.empty[HttpHeader], None)
    }
  }
}
