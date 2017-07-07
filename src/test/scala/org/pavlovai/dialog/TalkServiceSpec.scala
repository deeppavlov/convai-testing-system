package org.pavlovai.dialog

import akka.actor.{Actor, ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.pavlovai.dialog.TalkService.AssembleDialogs
import org.pavlovai.user.User
import org.pavlovai.user.UserService.{AddHoldedUsersToTalk, HoldUsers}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._
import scala.util.Random

/**
  * @author vadim
  * @since 07.07.17
  */
class TalkServiceSpec extends TestKit(ActorSystem("TalkServiceSpec")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "A TalkServiceSpec actor" must {

    "send HoldUsers(2) to UserService when AssembleDialogs received" in {
      val probe = TestProbe()
      val talkService = system.actorOf(TalkService.props(probe.ref))
      talkService ! AssembleDialogs
      probe.expectMsg(HoldUsers(2))
    }

    "send AddHoldedUsersToTalk to UserService when response on HoldUsers(2) received" in {
      val probe = TestProbe()
      val talkService = system.actorOf(TalkService.props(probe.ref))
      talkService ! AssembleDialogs
      probe.expectMsg(HoldUsers(2))
      probe.reply(List(User(1), User(2)))
      probe.expectMsgPF(1.second) { case AddHoldedUsersToTalk(List(User(1), User(2)), _) => () }
      probe.expectMsg(HoldUsers(2))
      probe.reply(List(User(3), User(4)))
      probe.expectMsgPF(1.second) { case AddHoldedUsersToTalk(List(User(3), User(4)), _) => () }
      probe.expectMsg(HoldUsers(2))
      probe.reply(List.empty)
      expectNoMsg(2.seconds)
    }

    "idle if received Nil on HoldUsers(2)" in {
      val probe = TestProbe()
      val talkService = system.actorOf(TalkService.props(probe.ref))
      talkService ! AssembleDialogs
      probe.expectMsg(HoldUsers(2))
      probe.reply(List.empty)
      expectNoMsg(2.seconds)
    }
  }

  class FakeUserService(usersInStoreCount: Int) extends Actor {
    import org.pavlovai.user.UserService._

    private var availableUsers = usersInStoreCount

    override def receive: Receive = {
      case HoldUsers(count: Int) =>
        if (availableUsers < count) sender ! None else sender ! Some((1 to count).map(_ => User(Random.nextInt())).toList)
        availableUsers -= count
      case AddHoldedUsersToTalk(user: List[User], dialog: ActorRef) =>
      case DeactivateUsers(user: List[User]) =>

      case MessageTo(user: User, text: String) =>
    }
  }
}
