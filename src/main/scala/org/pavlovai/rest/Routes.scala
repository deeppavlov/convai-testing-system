package org.pavlovai.rest

import java.util.Base64

import akka.actor.ActorRef
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.scalalogging.Logger
import info.mukel.telegrambot4s.marshalling.HttpMarshalling.toJson
import info.mukel.telegrambot4s.models.{Message, Update}
import spray.json.{JsValue, _}

/**
  * @author vadim
  * @since 10.07.17
  */
object Routes extends Directives with DefaultJsonProtocol with SprayJsonSupport {

  import BotService._
  import akka.http.scaladsl.unmarshalling.Unmarshaller._

  def route(botService: ActorRef, log: Logger)(implicit timeout: Timeout, materializer: ActorMaterializer): Route =

    path(""".+""".r / "sendMessage") { token =>
      post {
        entity(as[SendMes](messageUnmarshallerFromEntityUnmarshaller(sprayJsonUnmarshaller(sendMesFormat)))) { case SendMes(to, mes) =>
          import info.mukel.telegrambot4s.marshalling.HttpMarshalling._
          val r = (botService ? BotService.SendMessage(token, to, mes)).mapTo[Message]
          onComplete(r) {
            case util.Success(m) => complete(toJson(m))
            case util.Failure(ex) => complete(StatusCodes.InternalServerError)
          }
        }
      }
    } ~ get {
    pathPrefix(""".+""".r / "getUpdates") { token =>
      val lO = (botService ? BotService.GetMessages(token)).mapTo[Seq[Update]]
      onComplete(lO) {
        case util.Success(l) =>
          import info.mukel.telegrambot4s.marshalling.HttpMarshalling._
          complete(toJson(l))
        case util.Failure(ex) =>
          log.warn("erroe while getUpdates processing: " + ex.toString)
          complete(StatusCodes.BadRequest)
      }
    }
  }

  implicit val sendMesFormat = new RootJsonFormat[SendMes] {
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

  case class SendMes(chat_id: Int, message: BotMessage)
}

