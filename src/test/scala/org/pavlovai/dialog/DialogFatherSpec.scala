package org.pavlovai.dialog

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import org.pavlovai.communication.{Endpoint, Human, TelegramChat}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Success, Try}

/**
  * @author vadim
  * @since 14.07.17
  */
class DialogFatherSpec extends TestKit(ActorSystem("BotEndpointSpec", ConfigFactory.parseString(
  """
    |talk {
    |  talk_timeout = 5 minutes
    |  talk_length_max = 1000
    |  bot {
    |    talk_period_min = 1 second
    |  }
    |}
  """.stripMargin))) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  val textGenerator: ContextQuestions = new ContextQuestions {
    override def selectRandom: Try[String] = Success("test")
  }

  case class Tester(chatId: Long) extends Human

  "dialog human user" must {
    "see 'Sorry, wait for the opponent' message if no opponent fond" in {
      val gate = TestProbe()
      val daddy = system.actorOf(DialogFather.props(gate.ref, textGenerator))
      daddy ! DialogFather.UserAvailable(Tester(5))
      gate.expectMsg(Endpoint.DeliverMessageToUser(Tester(5), "Sorry, wait for the opponent", None))
      gate.expectNoMsg()
    }

    "see dialog context if opponent found" in {
      val gate = TestProbe()
      for (_ <- 1 to 3) {
        val daddy = system.actorOf(DialogFather.props(gate.ref, textGenerator))
        daddy ! DialogFather.UserAvailable(Tester(1))
        gate.expectMsg(Endpoint.DeliverMessageToUser(Tester(1), "Sorry, wait for the opponent", None))
        daddy ! DialogFather.UserAvailable(Tester(2))
        val t: ActorRef = gate.expectMsgPF(3.seconds) { case Endpoint.ActivateTalkForUser(Tester(1), tr) => tr }
        gate.expectMsg(Endpoint.ActivateTalkForUser(Tester(2), t))

        gate.expectMsg(Endpoint.DeliverMessageToUser(Tester(1), "test", Some(t.hashCode())))
        gate.expectMsg(Endpoint.DeliverMessageToUser(Tester(2), "test", Some(t.hashCode())))
      }
      gate.expectNoMsg()
    }

    "see own and opponent messages" in pending

    "evaluate dialog when other user finish dialog and then start next dialog" in {

      val gate = TestProbe()
      val daddy = system.actorOf(DialogFather.props(gate.ref, textGenerator))
      daddy ! DialogFather.UserAvailable(Tester(1))
      gate.expectMsg(Endpoint.DeliverMessageToUser(Tester(1), "Sorry, wait for the opponent", None))
      daddy ! DialogFather.UserAvailable(Tester(2))
      val t: ActorRef = gate.expectMsgPF(3.seconds) { case Endpoint.ActivateTalkForUser(Tester(1), tr) => tr }
      gate.expectMsg(Endpoint.ActivateTalkForUser(Tester(2), t))

      gate.expectMsg(Endpoint.DeliverMessageToUser(Tester(1), "test", Some(t.hashCode())))
      gate.expectMsg(Endpoint.DeliverMessageToUser(Tester(2), "test", Some(t.hashCode())))

      daddy ! DialogFather.UserLeave(Tester(2))

      gate.expectMsg(Endpoint.AskEvaluationFromHuman(Tester(1), s"Chat is finished, please evaluate the quality"))
      gate.expectMsg(Endpoint.AskEvaluationFromHuman(Tester(2), s"Chat is finished, please evaluate the quality"))

      t ! Dialog.PushMessageToTalk(Tester(1), "1")
      gate.expectMsg(Endpoint.AskEvaluationFromHuman(Tester(1), s"Please evaluate the breadth"))

      t ! Dialog.PushMessageToTalk(Tester(1), "1")
      gate.expectMsg(Endpoint.AskEvaluationFromHuman(Tester(1), s"Please evaluate the engagement"))

      t ! Dialog.PushMessageToTalk(Tester(1), "1")

      gate.expectMsg(Endpoint.DeliverMessageToUser(Tester(1), "Thank you!", Some(t.hashCode())))

      gate.expectNoMsg()
    }

    "evaluate dialog when sent /end and then not receive any messages" in pending
  }

}
