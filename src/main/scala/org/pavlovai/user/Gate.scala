package org.pavlovai.user

/**
  * @author vadim
  * @since 11.07.17
  */
object Gate {
  case class DeliverMessageToUser(receiver: User, message: String)
  case class PushMessageToTalk(from: User, message: String)
}
