package ai.ipavlov.communication.user

/**
  * @author vadim
  * @since 26.09.17
  */
class Client {

}

object Client {
  sealed trait ClientCommand
  case class ShowChatMessage(userId: String, messageId: String, text: String)
  case class ShowSystemNotification(userId: String, text: String)
  case class ShowEvaluationMessage(userId: String, text: String)
  case class ShowLastNotificationInDialog(userId: String, text: String)
}
