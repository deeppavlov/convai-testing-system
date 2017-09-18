package ai.ipavlov.communication.fbmessager

import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import akka.http.scaladsl.model.{HttpRequest, StatusCodes}
import akka.http.scaladsl.server.{Directive0, Directives}
import akka.stream.Materializer
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.codec.binary.Hex

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * @author vadim
  * @since 18.09.17
  */
trait RouteSupport extends LazyLogging with Directives {

  private val appSecret = ""

  def verifyPayload(req: HttpRequest)
                   (implicit materializer: Materializer, ec: ExecutionContext): Directive0 = {

    def isValid(payload: Array[Byte], secret: String, expected: String): Boolean = {
      val secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1")
      val mac = Mac.getInstance("HmacSHA1")
      mac.init(secretKeySpec)
      val result = mac.doFinal(payload)

      val computedHash = Hex.encodeHex(result).mkString
      logger.info(s"Computed hash: $computedHash")

      computedHash == expected
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

}
