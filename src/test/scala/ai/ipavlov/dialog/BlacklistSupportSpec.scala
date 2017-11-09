package ai.ipavlov.dialog

import java.time.Clock

import ai.ipavlov.communication.Endpoint
import ai.ipavlov.communication.user.{Bot, Human}
import akka.actor.{ActorSystem, Props}
import akka.testkit.{TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.Future

case class Tester(username: String, address: String) extends Human

class BlacklistSupportSpec extends TestKit(ActorSystem()) with WordSpecLike with Matchers with BeforeAndAfterAll {
  "DialogFather" should {
    "not to create dialogs with blocked users" in {
      val gate = TestProbe()
      val storage = TestProbe()
      val clck = Clock.systemDefaultZone()
      val daddy = system.actorOf(
        Props(new DialogFather(gate.ref, SquadQuestions, storage.ref, clck, scala.util.Random) with BalancedDialogConstructionRules with BlacklistSupport {
          def blacklist = Future.successful(Set(Tester("bannedUser", "vasya")))
        })
      )
      gate.expectMsg(Endpoint.SetDialogFather(daddy))

      daddy ! DialogFather.UserAvailable(Tester("bannedUser", "vasya"), 1)
      gate.expectMsg(Endpoint.ShowSystemNotificationToUser(Tester("bannedUser", "vasya"), "Please wait for your partner."))
      daddy ! DialogFather.UserAvailable(Bot("bot1"), 1)
      daddy ! DialogFather.UserAvailable(Tester("user", "petya"), 1)
      gate.expectMsg(Endpoint.ShowSystemNotificationToUser(Tester("user", "petya"), "Please wait for your partner."))
    }
  }
}
