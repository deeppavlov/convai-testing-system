package org.pavlovai.user

/**
  * @author vadim
  * @since 06.07.17
  */
sealed trait User {
  val chat_id: Long
}

case class Bot(chat_id: Long) extends User
case class Human(chat_id: Long) extends User
