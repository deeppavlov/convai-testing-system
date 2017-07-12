package org.pavlovai.user

/**
  * @author vadim
  * @since 06.07.17
  */
sealed trait UserWithChat {
  val chat_id: Long
}

case class BotUserWithChat(chat_id: Long, botId: String) extends UserWithChat
case class HumanUserWithChat(chat_id: Long, token: String) extends UserWithChat
