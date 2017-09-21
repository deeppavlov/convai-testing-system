package ai.ipavlov.communication.fbmessager

import ai.ipavlov.communication.rest.BotEndpoint.BotMessage
import ai.ipavlov.communication.rest.Routes.SendMes
import spray.json.{DefaultJsonProtocol, JsNumber, JsObject, JsString, JsValue, RootJsonFormat, serializationError}

/**
  * @author vadim
  * @since 18.09.17
  */
case class FBButton(`type`: String, title: String, payload: String)

//case class Payload(url: String)
trait FBPayload
case class FBButtonsPayload(text: String, buttons: List[FBButton]) extends FBPayload

case class FBAttachment(`type`: String, payload: FBPayload)

case class FBMessage(mid: Option[String] = None,
                     seq: Option[Long] = None,
                     text: Option[String] = None,
                     metadata: Option[String] = None,
                     attachment: Option[FBAttachment] = None)

case class FBSender(id: String)

case class FBRecipient(id: String)

case class FBMessageEventIn(sender: FBSender,
                            recipient: FBRecipient,
                            timestamp: Long,
                            message: FBMessage)

case class FBMessageEventOut(recipient: FBRecipient, message: FBMessage)

case class FBEntry(id: String,
                   time: Long, messaging:
                   List[FBMessageEventIn])

case class FBPObject(`object`: String, entry: List[FBEntry])

object FBMessageEventOut extends DefaultJsonProtocol {
  implicit val format: RootJsonFormat[FBMessageEventOut] = jsonFormat2(FBMessageEventOut(_,_))
}

object FBPObject extends DefaultJsonProtocol {
  implicit val format: RootJsonFormat[FBPObject] = jsonFormat2(FBPObject(_,_))
}

object FBMessageEventIn extends DefaultJsonProtocol {
  implicit val format: RootJsonFormat[FBMessageEventIn] = jsonFormat4(FBMessageEventIn(_,_, _, _))
}

object FBEntry extends DefaultJsonProtocol {
  implicit val format: RootJsonFormat[FBEntry] = jsonFormat3(FBEntry(_, _, _))
}

object FBMessage extends DefaultJsonProtocol {
  implicit val format: RootJsonFormat[FBMessage] = jsonFormat5(FBMessage(_, _, _, _, _))
}

object FBAttachment extends DefaultJsonProtocol {
  implicit val format: RootJsonFormat[FBAttachment] = jsonFormat2(FBAttachment(_, _))
}

object FBSender extends DefaultJsonProtocol {
  implicit val format: RootJsonFormat[FBSender] = jsonFormat1(FBSender(_))
}

object FBRecipient extends DefaultJsonProtocol {
  implicit val format: RootJsonFormat[FBRecipient] = jsonFormat1(FBRecipient(_))
}

object FBPayload extends DefaultJsonProtocol {
  import spray.json._

  private implicit val sendMesFormat: RootJsonFormat[FBPayload] = new RootJsonFormat[FBPayload] {
    override def write(obj: FBPayload): JsValue = obj match {
      case FBButtonsPayload(text, buttons) =>
        JsObject("template_type" -> "button".toJson, "text" -> text.toJson, "buttons" -> buttons.toJson)
    }

    override def read(json: JsValue): FBPayload = json.asJsObject.getFields("chat_id", "text") match {
      case Seq(JsString(button), JsString(text), JsArray(arr)) => FBButtonsPayload(text, List.empty)
      case _ => serializationError(s"Invalid json format: $json")
    }
  }
}


object FBButton extends DefaultJsonProtocol {
  implicit val format: RootJsonFormat[FBButton] = jsonFormat3(FBButton(_, _, _))
}