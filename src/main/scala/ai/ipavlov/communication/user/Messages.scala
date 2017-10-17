package ai.ipavlov.communication.user

import scala.util.Random

/**
  * @author vadim
  * @since 25.09.17
  */
object Messages {
  val notSupported = "Messages of this type aren't supported \uD83D\uDE1E"

  val youCantDoItNow =  "Sorry, you can't to do it now \uD83D\uDE1E"

  val pleaseWait =  "Please, wait..."

  val exit = """`(system msg):` exit"""

  val lastNotificationInDialog = """Thank you! It was great! Please choose /begin to continue evaluation."""

  val robotFace = "\uD83E\uDD16 "
  def randomFace = Random.shuffle(List("\uD83D\uDC6E", "\uD83D\uDC70", "\uD83D\uDC71", "\uD83D\uDC72", "\uD83D\uDC73",
    "\uD83D\uDC74", "\uD83D\uDC75", "\uD83D\uDC76", "\uD83D\uDC77", "\uD83D\uDC78", "\uD83D\uDC79", "\uD83D\uDC7A",
    "\uD83D\uDC7B", "\uD83D\uDC7C", "\uD83D\uDC7D", "\uD83D\uDC7F", "\uD83D\uDC80", "\uD83D\uDC81", "\uD83D\uDC82")).head + " "
}
