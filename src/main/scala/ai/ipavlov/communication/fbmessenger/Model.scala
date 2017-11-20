package ai.ipavlov.communication.fbmessenger

import spray.json.{DefaultJsonProtocol, RootJsonFormat}

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
                     seq: Option[Int] = None,
                     text: Option[String] = None,
                     metadata: Option[String] = None,
                     attachment: Option[FBAttachment] = None,
                     quick_replies: Option[List[FBQuickReply]] = None,
                     quick_reply: Option[FBQuickReply] = None
                    )

case class FBPostback(payload: String, title: String)

case class FBQuickReply(payload: String, title: Option[String] = None, content_type: Option[String] = Some("text"))

case class FBSender(id: String)

case class FBRecipient(id: String)

case class FBMessageEventIn(sender: FBSender,
                            recipient: FBRecipient,
                            timestamp: Long,
                            message: Option[FBMessage] = None,
                            postback: Option[FBPostback] = None
                           )

case class FBMessageEventOut(recipient: FBRecipient, message: FBMessage)

case class FBEntry(id: String,
                   time: Long,
                   messaging: List[FBMessageEventIn],
                   standby: List[FBMessageEventIn]
                  )

case class FBPObject(`object`: String, entry: List[FBEntry])

object FBMessageEventOut extends DefaultJsonProtocol {
  implicit val format: RootJsonFormat[FBMessageEventOut] = jsonFormat2(FBMessageEventOut(_,_))
}

object FBPObject extends DefaultJsonProtocol {
  implicit val format: RootJsonFormat[FBPObject] = jsonFormat2(FBPObject(_,_))
}

object FBMessageEventIn extends DefaultJsonProtocol {
  implicit val format: RootJsonFormat[FBMessageEventIn] = jsonFormat5(FBMessageEventIn(_, _, _, _, _))
}

object FBEntry extends DefaultJsonProtocol {
  implicit val format: RootJsonFormat[FBEntry] = jsonFormat4(FBEntry(_, _, _, _))
}

object FBMessage extends DefaultJsonProtocol {
  implicit val format: RootJsonFormat[FBMessage] = jsonFormat7(FBMessage(_, _, _, _, _, _, _))
}

/*object FBQuickReply extends DefaultJsonProtocol {
  implicit val formatT: RootJsonFormat[FBQuickReply] = new RootJsonFormat[FBQuickReply] {
    import spray.json._
    override def write(obj: FBQuickReply): JsValue = obj match {
      case FBQuickReply(title, payload) =>
        JsObject("title" -> title.toJson, "payload" -> payload.toJson, "content_type" -> "text".toJson)
      case _ => deserializationError(s"unsupported format")
    }

    override def read(json: JsValue): FBQuickReply = json.asJsObject.getFields("payload", "title") match {
      case payload :: Nil => FBQuickReply(payload.toString(), None)
      case payload :: title :: Nil => FBQuickReply(payload.toString(), Some(title.toString()))
      case _ => serializationError(s"Invalid json format: $json")
    }
  }
}*/

object FBQuickReply extends DefaultJsonProtocol {
  implicit val format: RootJsonFormat[FBQuickReply] = jsonFormat3(FBQuickReply(_, _, _))
}

object FBSender extends DefaultJsonProtocol {
  implicit val format: RootJsonFormat[FBSender] = jsonFormat1(FBSender(_))
}

object FBRecipient extends DefaultJsonProtocol {
  implicit val format: RootJsonFormat[FBRecipient] = jsonFormat1(FBRecipient(_))
}

object FBButton extends DefaultJsonProtocol {
  implicit val format: RootJsonFormat[FBButton] = jsonFormat3(FBButton(_, _, _))
}

object FBPostback extends DefaultJsonProtocol {
  implicit val format: RootJsonFormat[FBPostback] = jsonFormat2(FBPostback(_, _))
}

object FBAttachment extends DefaultJsonProtocol {
  implicit val formatT: RootJsonFormat[FBPayload] = new RootJsonFormat[FBPayload] {
    import spray.json._
    override def write(obj: FBPayload): JsValue = obj match {
      case FBButtonsPayload(text, buttons) =>
        JsObject("template_type" -> "button".toJson, "text" -> text.toJson, "buttons" -> buttons.toJson)
      case _ => deserializationError(s"unsupported format")
    }

    override def read(json: JsValue): FBPayload = json.asJsObject.getFields("chat_id", "text") match {
      case Seq(JsString(button), JsString(text), JsArray(arr)) => FBButtonsPayload(text, List.empty)
      case _ => serializationError(s"Invalid json format: $json")
    }
  }

  implicit val format: RootJsonFormat[FBAttachment] = jsonFormat2(FBAttachment(_, _))
}