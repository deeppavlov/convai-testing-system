package org.pavlovai.dialog

import akka.actor.{Actor, ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.pavlovai.dialog.TalkService.AssembleDialogs
import org.pavlovai.user.{UserWithChat, HumanUserWithChat}
import org.pavlovai.user.ChatRepository.{AddHoldedChatsToTalk, HoldChats}
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
      val talkService = system.actorOf(TalkService.props(probe.ref, probe.ref))
      talkService ! AssembleDialogs
      probe.expectMsg(HoldChats(2))
    }

    "send AddHoldedUsersToTalk to UserService when response on HoldUsers(2) received" in {
      val probeCatRepo = TestProbe()
      val probeApi = TestProbe()
      val talkService = system.actorOf(TalkService.props(probeCatRepo.ref, probeApi.ref))
      talkService ! AssembleDialogs
      probeCatRepo.expectMsg(HoldChats(2))
      probeCatRepo.reply(List(HumanUserWithChat(1, "vasya"), HumanUserWithChat(2, "petya")))
      probeCatRepo.expectMsgPF(1.second) { case AddHoldedChatsToTalk(List(HumanUserWithChat(1, "vasya"), HumanUserWithChat(2, "petya")), _) => () }
      probeCatRepo.expectMsg(HoldChats(2))
      probeCatRepo.reply(List(HumanUserWithChat(3, "dima"), HumanUserWithChat(4, "sereja")))
      probeCatRepo.expectMsgPF(1.second) { case AddHoldedChatsToTalk(List(HumanUserWithChat(3, "dima"), HumanUserWithChat(4, "sereja")), _) => () }
      probeCatRepo.expectMsg(HoldChats(2))
      probeCatRepo.reply(List.empty)
      expectNoMsg(2.seconds)
    }

    "idle if received Nil on HoldUsers(2)" in {
      val probe = TestProbe()
      val talkService = system.actorOf(TalkService.props(probe.ref, probe.ref))
      talkService ! AssembleDialogs
      probe.expectMsg(HoldChats(2))
      probe.reply(List.empty)
      expectNoMsg(2.seconds)
    }
  }
}
