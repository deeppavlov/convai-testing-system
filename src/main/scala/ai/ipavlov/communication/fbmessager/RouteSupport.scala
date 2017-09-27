package ai.ipavlov.communication.fbmessager

import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import ai.ipavlov.communication.Endpoint
import ai.ipavlov.communication.rest.Routes.logger
import ai.ipavlov.communication.user.{FbChat, User}
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.{HttpHeader, HttpRequest, StatusCode, StatusCodes}
import akka.http.scaladsl.server.{Directive0, Directives}
import akka.stream.{ActorMaterializer, Materializer}
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.codec.binary.Hex

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Try
import scala.util.control.NonFatal

/**
  * @author vadim
  * @since 18.09.17
  */
trait RouteSupport extends LazyLogging with Directives {

  def verifyPayload(req: HttpRequest, appSecret: String)
                   (implicit materializer: Materializer, ec: ExecutionContext): Directive0 = {

    def isValid(payload: Array[Byte], secret: String, expected: String): Boolean = {
      val secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1")
      val mac = Mac.getInstance("HmacSHA1")
      mac.init(secretKeySpec)
      val result = mac.doFinal(payload)

      val computedHash = Hex.encodeHex(result).mkString
      logger.info(s"Computed hash: $computedHash")

      val res = computedHash == expected

      if (!res) logger.warn("token verification failed")

      res
    }

    req.headers.find(_.name == "X-Hub-Signature").map(_.value()) match {
      case Some(token) =>
        val payload =
          Await.result(req.entity.toStrict(5 seconds).map(_.data.decodeString("UTF-8")), 5 second)
        logger.info(s"Receive token $token and payload $payload")
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
                            materializer: ActorMaterializer):
  (StatusCode, List[HttpHeader], Option[Either[String, String]]) = {
    logger.info(s"Receive fbObject: $fbObject")
    fbObject.entry.foreach { entry =>
      entry.messaging.foreach { me =>
        val senderId = me.sender.id
        val message = me.message
        message.text match {
          case Some(text) if senderId != "1676239152448347" => endpoint ! Endpoint.MessageFromUser(FbChat(senderId), text)
          case Some(_) =>
          case None =>
            logger.info("Receive image")
        }
      }
    }
    (StatusCodes.OK, List.empty[HttpHeader], None)
  }

  def verifyToken(token: String, mode: String, challenge: String, originalToken: String)
                         (implicit ec: ExecutionContext):
  (StatusCode, List[HttpHeader], Option[Either[String, String]]) = {

    if (mode == "subscribe" && token == originalToken) {
      logger.info(s"Verify webhook token: $token, mode $mode")
      (StatusCodes.OK, List.empty[HttpHeader], Some(Left(challenge)))
    }
    else {
      logger.error(s"Invalid webhook token: $token, mode $mode")
      (StatusCodes.Forbidden, List.empty[HttpHeader], None)
    }
  }
}
