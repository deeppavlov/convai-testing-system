package org.pavlovai.user

import akka.actor.ActorRef
import org.pavlovai

/**
  * @author vadim
  * @since 07.07.17
  */
object UserService {
  case class HoldUsers(count: Int)
  case class AddHoldedUsersToTalk(user: List[User], dialog: ActorRef)
  case class DeactivateUsers(user: List[User])

  case class MessageTo(user: User, text: String)
}
