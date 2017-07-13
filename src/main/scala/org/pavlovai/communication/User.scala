package org.pavlovai.communication

/**
  * @author vadim
  * @since 06.07.17
  */
trait User

case class Bot(token: String) extends User

sealed trait Human extends User {
  val chatId: Long
}
case class TelegramChat(chatId: Long) extends Human
