package ai.ipavlov.communication.user

/**
  * @author vadim
  * @since 26.09.17
  */
class Client {

}

object Client {
  sealed trait ClientCommand
  case class ShowChatMessage(address: String, messageId: String, text: String)
  case class ShowSystemNotification(address: String, text: String)
  case class ShowEvaluationMessage(address: String, text: String)
  case class ShowLastNotificationInDialog(address: String, text: String)
  case class ShowHelpMessage(address: String)
}
