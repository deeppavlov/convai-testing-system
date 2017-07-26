package org.pavlovai.communication.rest

import java.time.{Clock, Instant, ZoneId}

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import info.mukel.telegrambot4s.models.{Chat, ChatType, Message, Update}
import org.pavlovai.communication.rest.BotEndpoint.GetMessages
import org.pavlovai.communication.{Bot, Endpoint}
import org.pavlovai.dialog.DialogFather.UserAvailable
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

/**
  * @author vadim
  * @since 13.07.17
  */
class BotEndpointSpec extends TestKit(ActorSystem("BotEndpointSpec", ConfigFactory.parseString(
  """
    |bot {
    |  registered = [ { token: "0", max_connections: 1000 }, { token: "1", max_connections: 1000 }, { token: "2", max_connections: 1000 }]
    |  talk_period_min = 1 second
    |}
  """.stripMargin))) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  case object BrokenClock extends Clock {
    override def withZone(zone: ZoneId): Clock = this

    override def getZone: ZoneId = ZoneId.systemDefault()

    override def instant(): Instant = Instant.ofEpochMilli(0)
  }

  "BotEndpoint initialization" should {
    "send UserAvailable to dialog constructor for each bots" in {
      val daddy = TestProbe()
      system.actorOf(BotEndpoint.props(daddy.ref, BrokenClock))
      daddy.expectMsg(UserAvailable(Bot("0"), 1000))
      daddy.expectMsg(UserAvailable(Bot("1"), 1000))
      daddy.expectMsg(UserAvailable(Bot("2"), 1000))
    }
  }

  "BotEndpoint DeliverMessageToUser command" should {
    "put Update message to valid queue with valid chat id" in {
      val daddy = TestProbe()
      val ep = system.actorOf(BotEndpoint.props(daddy.ref, BrokenClock))
      val dialog1 = TestProbe()
      ep ! Endpoint.ActivateTalkForUser(Bot("0"), dialog1.ref)
      ep ! Endpoint.ChatMessageToUser(Bot("0"), "test1", dialog1.ref.hashCode(), Instant.now().getNano)
      ep ! GetMessages("0")
      expectMsg(List(Update(0, Some(Message(0, None, Instant.now(BrokenClock).getNano, Chat(dialog1.ref.hashCode(), ChatType.Private), text = Some("test1"))))))
      ep ! GetMessages("0")
      expectMsg(List.empty)
      ep ! Endpoint.ChatMessageToUser(Bot("0"), "test2", dialog1.ref.hashCode(), Instant.now().getNano)
      val dialog2 = TestProbe()
      ep ! Endpoint.ActivateTalkForUser(Bot("0"), dialog2.ref)
      ep ! Endpoint.ChatMessageToUser(Bot("0"), "test3", dialog2.ref.hashCode(), Instant.now().getNano)
      ep ! GetMessages("0")
      expectMsg(List(
        Update(0, Some(Message(0, None, Instant.now(BrokenClock).getNano, Chat(dialog1.ref.hashCode(), ChatType.Private), text = Some("test2")))),
        Update(0, Some(Message(0, None, Instant.now(BrokenClock).getNano, Chat(dialog2.ref.hashCode(), ChatType.Private), text = Some("test3"))))
      ))
    }
  }

}
