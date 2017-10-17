package ai.ipavlov.dialog

import java.time.Clock

import ai.ipavlov.communication.Endpoint
import ai.ipavlov.communication.user.{Bot, FbChat}
import ai.ipavlov.dialog.Dialog.PushMessageToTalk
import akka.actor.ActorSystem
import akka.testkit.{TestActorRef, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

/**
  * @author vadim
  * @since 28.09.17
  */
class DialogSpec extends TestKit(ActorSystem("BotEndpointSpec")) with WordSpecLike with Matchers with BeforeAndAfterAll {
  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "dialog actor" must {
    "construct valid dialog between bot an facebook user" in {
      val gate = TestProbe()
      val storage = TestProbe()
      val dialog = TestActorRef(new Dialog(FbChat("1", "1"), Bot("2"), "ololo", gate.ref, storage.ref, Clock.systemDefaultZone()))
      dialog ! PushMessageToTalk(FbChat("1", "1"), "!!!")
      gate.expectMsgPF(3.seconds) { case Endpoint.ChatMessageToUser(Bot("2"), _, "!!!", _, _) => }
    }
  }
}
