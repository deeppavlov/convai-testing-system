package org.pavlovai.communication

/**
  * @author vadim
  * @since 06.07.17
  */
trait User {
  val id: String
}

case class Bot(token: String) extends User {
  val id: String = token
}

trait Human extends User {
  val chatId: Long
  val id: String = chatId.toString
}
case class TelegramChat(chatId: Long) extends Human
