package ai.ipavlov.communication.user

/**
  * @author vadim
  * @since 25.09.17
  */
object Messages {
  val helpMessage: String = """
    |1. Please set your Username in Settings menu.
    |    - MacOS & iOS:
    |    Gear ("Settings") button to bottom left, after that "Username";
    |    - Windows & Linux & Android:
    |    Menu button left top, "Settings" and "Username" field.
    |2. To start a dialog type or choose a /begin command .
    |3. You will be connected to a peer or, if no peer is available at the moment, you’ll receive the message from @ConvaiBot `Please wait for you partner.`.
    |4. Peer might be a bot or another human evaluator.
    |5. After you were connected with your peer you will receive a starting message - a passage or two from a Wikipedia article.
    |6. Your task is to discuss the content of a presented passage with the peer and score her/his replies.
    |7. Please score every utterance of your peer with a ‘thumb UP’ button if you like it, and ‘thumb DOWN’ button in the opposite case.
    |8. To finish the conversation type or choose a command /end.
    |9. When the conversation is finished, you will receive a request from @ConvaiBot to score the overall quality of the dialog along three dimensions:
    |    - quality - how much are you satisfied with the whole conversation?
    |    - breadth - in your opinion was a topic discussed thoroughly or just from one side?
    |    - engagement - was it interesting to participate in this conversation?
    |10. If your peer ends the dialog before you, you will also receive a scoring request from @ConvaiBot.
    |11. Your conversations with a peer will be recorded for further use. By starting a chat you give permission for your anonymised conversation data to be released publicly under [Apache License Version 2.0](https://www.apache.org/licenses/LICENSE-2.0).
  """.stripMargin

  val notSupported = "Messages of this type aren't supported \uD83D\uDE1E"

  val exit = """`(system msg):` exit"""

  val lastNotificationInDialog = """Thank you! It was great! Please choose /begin to continue evaluation."""
}
