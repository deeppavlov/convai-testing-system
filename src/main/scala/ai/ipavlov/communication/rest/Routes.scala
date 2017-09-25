package ai.ipavlov.communication.rest

import ai.ipavlov.communication.FbChat
import ai.ipavlov.communication.fbmessager.{FBPObject, RouteSupport}
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{HttpHeader, HttpRequest, StatusCode, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import info.mukel.telegrambot4s.models.{Message, Update}
import spray.json._

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.Try
import scala.util.control.NonFatal

/**
  * @author vadim
  * @since 10.07.17
  */
object Routes extends Directives with DefaultJsonProtocol with SprayJsonSupport with LazyLogging with RouteSupport {

  import BotEndpoint._
  import akka.http.scaladsl.unmarshalling.Unmarshaller._

  implicit val timeout: Timeout = 5.seconds

  def route(botService: ActorRef, fbService: ActorRef, fbSecret: String, callbackToken: String, pageAccessToken: String)(implicit materializer: ActorMaterializer, ec: ExecutionContext, system: ActorSystem): Route = extractRequest { request: HttpRequest =>

    post {
      path(""".+""".r / "sendMessage") { token =>
        entity(as[SendMes](messageUnmarshallerFromEntityUnmarshaller(sprayJsonUnmarshaller(sendMesFormat)))) { case SendMes(to, mes) =>
          import info.mukel.telegrambot4s.marshalling.HttpMarshalling._
          val r = (botService ? BotEndpoint.SendMessage(token, to, mes)).mapTo[Message]
          onComplete(r) {
            case util.Success(m) => complete(toJson(m))
            case util.Failure(ex) => complete(StatusCodes.InternalServerError)
          }
        }
      }
    } ~ get {
      pathPrefix(""".+""".r / "getUpdates") { token =>
        val lO = (botService ? BotEndpoint.GetMessages(token)).mapTo[Seq[Update]]
        onComplete(lO) {
          case util.Success(l) =>
            import info.mukel.telegrambot4s.marshalling.HttpMarshalling._
            complete(toJson(l))
          case util.Failure(ex) =>
            //log.warn("erroe while getUpdates processing: " + ex.toString)
            complete(StatusCodes.BadRequest)
        }
      }
    } ~ get {
      path("webhook") {
        parameters("hub.verify_token", "hub.mode", "hub.challenge") {
          (tokenFromFb, mode, challenge) => complete {
            verifyToken(tokenFromFb, mode, challenge, callbackToken)
          }
        }
      }
    } ~ post {
      verifyPayload(request, fbSecret)(materializer, ec) {
        path("webhook") {
          entity(as[FBPObject]) { fbObject =>
            complete {
              handleMessage(fbService, fbObject, pageAccessToken)
            }
          }
        }
      }
    }
  }

  private def handleMessage(fbService: ActorRef, fbObject: FBPObject, pageAccessToken: String)
                   (implicit ec: ExecutionContext, system: ActorSystem,
                    materializer: ActorMaterializer):
  (StatusCode, List[HttpHeader], Option[Either[String, String]]) = {
    logger.info(s"Receive fbObject: $fbObject")
    fbObject.entry.foreach { entry =>
      entry.messaging.foreach { me =>
        val senderId = me.sender.id
        val message = me.message
        message.text match {
          case Some(text) =>
            Try(senderId.toLong)
              .map(id => fbService ! ai.ipavlov.communication.fbmessager.FBEndpoint.Message(FbChat(id), text))
              .recover {
                case NonFatal(e) => logger.error("can't parse to long from " + senderId, e)
              }
          case None =>
            logger.info("Receive image")
            Future.successful(())
        }
      }
    }
    (StatusCodes.OK, List.empty[HttpHeader], None)
  }

  private def verifyToken(token: String, mode: String, challenge: String, originalToken: String)
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

  private implicit val sendMesFormat: RootJsonFormat[SendMes] = new RootJsonFormat[SendMes] {
    override def write(obj: SendMes): JsValue = obj match {
      case SendMes(chat_id, message) =>
        val messageStr = message.toJson.toString()
        JsObject("chat_id" -> chat_id.toJson, "text" -> messageStr.toJson)
    }

    override def read(json: JsValue): SendMes = json.asJsObject.getFields("chat_id", "text") match {
      case Seq(JsNumber(chat_id), JsString(msg)) => SendMes(chat_id.toIntExact, msg.parseJson.convertTo[BotMessage])
      case _ => serializationError(s"Invalid json format: $json")
    }
  }

  private case class SendMes(chat_id: Int, message: BotMessage)
}

