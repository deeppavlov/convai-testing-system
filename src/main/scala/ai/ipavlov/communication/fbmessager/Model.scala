package ai.ipavlov.communication.fbmessager

import spray.json.{DefaultJsonProtocol, RootJsonFormat}

/**
  * @author vadim
  * @since 18.09.17
  */
case class Payload(url: String)

case class Attachment(`type`: String, payload: Payload)

case class FBMessage(mid: Option[String] = None,
                     seq: Option[Long] = None,
                     text: Option[String] = None,
                     metadata: Option[String] = None,
                     attachment: Option[Attachment] = None)

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

object Attachment extends DefaultJsonProtocol {
  implicit val format: RootJsonFormat[Attachment] = jsonFormat2(Attachment(_, _))
}

object FBSender extends DefaultJsonProtocol {
  implicit val format: RootJsonFormat[FBSender] = jsonFormat1(FBSender(_))
}

object FBRecipient extends DefaultJsonProtocol {
  implicit val format: RootJsonFormat[FBRecipient] = jsonFormat1(FBRecipient(_))
}

object Payload extends DefaultJsonProtocol {
  implicit val format: RootJsonFormat[Payload] = jsonFormat1(Payload(_))
}