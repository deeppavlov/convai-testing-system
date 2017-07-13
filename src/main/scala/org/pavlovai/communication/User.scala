package org.pavlovai.communication

/**
  * @author vadim
  * @since 06.07.17
  */
sealed trait User

case class Bot(token: String) extends User
case class TelegramChat(chat_id: Long) extends User
