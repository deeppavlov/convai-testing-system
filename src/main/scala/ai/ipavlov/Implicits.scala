package ai.ipavlov

import akka.actor.ActorRef

/**
  * @author vadim
  * @since 11.09.17
  */
trait Implicits {
  implicit class DialogActorRef(ref: ActorRef) {
    val chatId: Int = ref.hashCode()
  }
}
