package org.pavlovai.dialog

import akka.actor.{Actor, ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.pavlovai.dialog.DialogFather.AssembleDialogs
import org.pavlovai.communication.{User, TelegramChat}
import org.pavlovai.communication.ChatRepository.{AddHoldedChatsToTalk, HoldChats}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._
import scala.util.Random

/**
  * @author vadim
  * @since 07.07.17
  */
class DialogConstructorSpec extends TestKit(ActorSystem("TalkServiceSpec")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "A TalkServiceSpec actor" must {

  }
}
