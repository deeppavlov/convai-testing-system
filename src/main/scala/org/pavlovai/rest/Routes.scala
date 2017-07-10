package org.pavlovai.rest

import java.util.Base64

import akka.actor.ActorRef
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.scalalogging.Logger
import info.mukel.telegrambot4s.models.{Message, Update}
import org.pavlovai.rest.BotManager.{BotMessage, SendMes}
import spray.json.{DefaultJsonProtocol, JsValue, RootJsonFormat}

/**
  * @author vadim
  * @since 10.07.17
  */
object Routes extends SprayJsonSupport with DefaultJsonProtocol with Directives {

  import info.mukel.telegrambot4s.marshalling.HttpMarshalling._

  def route(botService: ActorRef, log: Logger)(implicit timeout: Timeout): Route = {

    /*get {
      pathPrefix(""".+""".r / "sendMessage") { token =>
        parameters('chat_id.as[Long], "text") { (chat_id, text) =>
          val decoded: String = Base64.getDecoder.decode(text).mkString
          val l = l.fromJson

          val r = (botService ? BotManager.SendMessage(token, chat_id, )).mapTo[Message]
          onComplete(r) {
            case util.Success(m) => complete(toJson(m))
            case util.Failure(ex) => complete(StatusCodes.InternalServerError)
          }
        }
      }
    } ~ */post {
      pathPrefix(""".+""".r / "sendMessage") { token =>
        entity(as[SendMes]) { case SendMes(to, mes) =>

          val r = (botService ? BotManager.SendMessage(token, to, mes)).mapTo[Message]
          onComplete(r) {
            case util.Success(m) => complete(toJson(m))
            case util.Failure(ex) => complete(StatusCodes.InternalServerError)
          }
        }
      }
    } ~ get {
      pathPrefix(""".+""".r / "getUpdates") { token =>
        val lO = (botService ? BotManager.GetMessages(token)).mapTo[Seq[Update]]
        onComplete(lO) {
          case util.Success(l) => complete(toJson(l))
          case util.Failure(ex) =>
            log.warn("erroe while getUpdates processing: " + ex.toString)
            complete(StatusCodes.BadRequest)
        }
      }
    }
  }
}
