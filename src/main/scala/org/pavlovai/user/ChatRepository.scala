package org.pavlovai.user

import akka.actor.{Actor, ActorLogging, ActorRef}
import org.pavlovai

/**
  * @author vadim
  * @since 07.07.17
  */

class ChatRepository extends Actor with ActorLogging {
  override def receive: Receive = ???
}

object ChatRepository {
  case class HoldChats(count: Int)
  case class AddHoldedChatsToTalk(user: List[UserWithChat], dialog: ActorRef)
  case class DeactivateChats(user: List[UserWithChat])
}


