package ai.ipavlov.communication

import ai.ipavlov.communication.user._
import ai.ipavlov.dialog.DialogFather
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestFSMRef, TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

/**
  * @author vadim
  * @since 28.09.17
  */
class UserSpec extends TestKit(ActorSystem("UserSpec", ConfigFactory.parseString(
  """
    |akka {
    |  loggers = ["akka.event.slf4j.Slf4jLogger"]
    |  loglevel = "DEBUG"
    |  logging-filter = "akka.event.slf4j.Slf4jLoggingFilter"
    |}
  """.stripMargin))) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  case object Tester extends Human {
    override val address: String = "tester-1"
    override val username: String = "tester-1"
  }

  "user FSM" should {
    "change state correctly" in {
      val daddy = TestProbe()
      val client = TestProbe()
      val user = TestFSMRef(new User(Tester, daddy.ref, client.ref))

      user ! User.Begin
      assert(user.stateName == WaitDialogCreation)

      daddy.expectMsg(DialogFather.UserAvailable(Tester, 1))
      val talk = TestProbe()
      user ! Endpoint.ActivateTalkForUser(Tester, talk.ref)
      assert(user.stateName == InDialog)

      user ! Endpoint.FinishTalkForUser(Tester, talk.ref)
      assert(user.stateName == Idle)
    }
  }

}
