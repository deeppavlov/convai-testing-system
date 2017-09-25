package ai.ipavlov.communication

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

case class TelegramChat(chatId: Long, username: Option[String]) extends Human {
  override def canEqual(a: Any): Boolean = a.isInstanceOf[TelegramChat]
  override def equals(that: Any): Boolean =
    that match {
      case that: TelegramChat => that.canEqual(this) && that.chatId == chatId
      case _ => false
    }
  override def hashCode: Int = { chatId.hashCode() }
}

case class FbChat(chatId: Long) extends Human {
  override def canEqual(a: Any): Boolean = a.isInstanceOf[TelegramChat]
  override def equals(that: Any): Boolean =
    that match {
      case that: FbChat => that.canEqual(this) && that.chatId == chatId
      case _ => false
    }
  override def hashCode: Int = { chatId.hashCode() }
}
