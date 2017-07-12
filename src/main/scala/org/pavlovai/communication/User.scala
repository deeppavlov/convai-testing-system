package org.pavlovai.communication

/**
  * @author vadim
  * @since 06.07.17
  */
sealed trait User

case class Bot(token: String) extends User
case class HumanChat(chat_id: Long, username: String) extends User
