package org.pavlovai.rest

import java.time.Instant

import akka.actor.{Actor, ActorLogging, Props}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import info.mukel.telegrambot4s.models.{Chat, ChatType, Message, Update}
import spray.json.{DefaultJsonProtocol, JsObject, JsValue, JsonFormat, RootJsonFormat, _}

import scala.util.Random

/**
  * @author vadim
  * @since 10.07.17
  */
class BotManager extends Actor with ActorLogging {
  import BotManager._

  private val rnd = Random

  override def receive: Receive = {
    case GetMessages(id) => sender ! Seq(Update(0, Some(Message(0, None, Instant.now().getNano, Chat(0, ChatType.Private), text = Some("test")))))
    //TODO
    case m @ SendMessage(id, to, NormalMessage(text, evaluation)) => sender ! Message(rnd.nextInt(), None, Instant.now().getNano, Chat(to, ChatType.Private), text = Some(m.toJson.toString))
    case m @ SendMessage(id, to, EndMessage(evaluation)) => sender ! Message(rnd.nextInt(), None, Instant.now().getNano, Chat(to, ChatType.Private), text = Some(m.toJson.toString()))
  }
}

object BotManager extends SprayJsonSupport with DefaultJsonProtocol  {

  def props: Props = Props[BotManager]

  sealed trait BotMessage
  case class FirstMessage(text: String) extends BotMessage
  case class NormalMessage(text: String, evaluation: Int) extends BotMessage
  case class SummaryEvaluation(quality: Int, breadth: Int, engagment: Int)
  case class EndMessage(evaluation: SummaryEvaluation) extends BotMessage

  case class SendMessage(id: String, to: Long, text: BotMessage)
  case class GetMessages(id: String)

  implicit val normalMessageFormat: RootJsonFormat[NormalMessage] = jsonFormat2(NormalMessage)
  implicit val summaryEvaluationFormat: RootJsonFormat[SummaryEvaluation] = jsonFormat3(SummaryEvaluation)
  implicit val endMessageFormat: RootJsonFormat[EndMessage] = jsonFormat1(EndMessage)
  implicit val firstMessageFormat: RootJsonFormat[FirstMessage] = jsonFormat1(FirstMessage)
  implicit val actionFormat1 = new JsonFormat[BotMessage] {
    override def write(obj: BotMessage): JsValue = obj match {
      case FirstMessage(text: String) => JsObject("text" -> text.toJson)
      case NormalMessage(text: String, evaluation: Int) => JsObject("text" -> text.toJson, "evaluation" -> evaluation.toJson)
      case EndMessage(SummaryEvaluation(quality, breadth, engagment)) => JsObject("quality" -> quality.toJson, "breadth" -> breadth.toJson, "engagement" -> engagment.toJson)
    }

    override def read(json: JsValue): BotMessage = json.asJsObject.getFields("text") match {
      case Seq(JsString("/end")) => json.convertTo[EndMessage]
      case _ if json.asJsObject.fields.contains("evaluation") => json.convertTo[NormalMessage]
      case _ if !json.asJsObject.fields.contains("evaluation") => json.convertTo[FirstMessage]
      case _ => throw new RuntimeException(s"Invalid json format: $json")
    }
  }
}
