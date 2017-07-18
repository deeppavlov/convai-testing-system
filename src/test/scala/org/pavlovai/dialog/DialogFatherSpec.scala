package org.pavlovai.dialog

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import org.pavlovai.communication.{Bot, Endpoint, Human, TelegramChat}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

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

  "human user after /begin command" must {
    "see 'Sorry, wait for the opponent' message if no opponent fond" in {
      val gate = TestProbe()
      val storage = TestProbe()
      val daddy = system.actorOf(DialogFather.props(gate.ref, textGenerator, storage.ref))
      gate.expectMsg(Endpoint.SetDialogFather(daddy))

      daddy ! DialogFather.UserAvailable(Tester(5))
      gate.expectMsg(Endpoint.DeliverMessageToUser(Tester(5), "Please wait for your partner.", None))
      gate.expectNoMsg()
    }

    "see dialog context if opponent found" in {
      val gate = TestProbe()
      val storage = TestProbe()
      for (_ <- 1 to 3) {
        val daddy = system.actorOf(DialogFather.props(gate.ref, textGenerator, storage.ref))
        gate.expectMsg(Endpoint.SetDialogFather(daddy))
        daddy ! DialogFather.UserAvailable(Tester(1))
        gate.expectMsg(Endpoint.DeliverMessageToUser(Tester(1), "Please wait for your partner.", None))
        daddy ! DialogFather.UserAvailable(Tester(2))
        val t: ActorRef = gate.expectMsgPF(3.seconds) { case Endpoint.ActivateTalkForUser(Tester(1), tr) => tr }
        gate.expectMsg(Endpoint.ActivateTalkForUser(Tester(2), t))

        gate.expectMsg(Endpoint.DeliverMessageToUser(Tester(1), "test", Some(t.hashCode())))
        gate.expectMsg(Endpoint.DeliverMessageToUser(Tester(2), "test", Some(t.hashCode())))
      }
      gate.expectNoMsg()
    }

    "see own and opponent messages" in pending

    "evaluate dialog when other user finish dialog" in {
      val gate = TestProbe()
      val storage = TestProbe()
      val daddy = system.actorOf(DialogFather.props(gate.ref, textGenerator, storage.ref))
      gate.expectMsg(Endpoint.SetDialogFather(daddy))
      daddy ! DialogFather.UserAvailable(Tester(1))
      gate.expectMsg(Endpoint.DeliverMessageToUser(Tester(1), "Please wait for your partner.", None))
      daddy ! DialogFather.UserAvailable(Tester(2))
      val talk: ActorRef = gate.expectMsgPF(3.seconds) { case Endpoint.ActivateTalkForUser(Tester(1), tr) => tr }
      gate.expectMsg(Endpoint.ActivateTalkForUser(Tester(2), talk))

      gate.expectMsg(Endpoint.DeliverMessageToUser(Tester(1), "test", Some(talk.hashCode())))
      gate.expectMsg(Endpoint.DeliverMessageToUser(Tester(2), "test", Some(talk.hashCode())))

      talk ! Dialog.PushMessageToTalk(Tester(1), "ololo")
      gate.expectMsg(Endpoint.DeliverMessageToUser(Tester(2), "ololo", Some(talk.hashCode())))

      daddy ! DialogFather.UserLeave(Tester(2))

      gate.expectMsgPF(3.seconds) {
        case Endpoint.AskEvaluationFromHuman(Tester(2), "Chat is finished, please evaluate the quality") =>
        case Endpoint.AskEvaluationFromHuman(Tester(1), "Chat is finished, please evaluate the quality") =>
      }

      gate.expectMsgPF(3.seconds) {
        case Endpoint.AskEvaluationFromHuman(Tester(2), "Chat is finished, please evaluate the quality") =>
        case Endpoint.AskEvaluationFromHuman(Tester(1), "Chat is finished, please evaluate the quality") =>
      }

      talk ! Dialog.PushMessageToTalk(Tester(1), "1")
      gate.expectMsg(Endpoint.AskEvaluationFromHuman(Tester(1), s"Please evaluate the breadth"))
      talk ! Dialog.PushMessageToTalk(Tester(1), "2")
      gate.expectMsg(Endpoint.AskEvaluationFromHuman(Tester(1), s"Please evaluate the engagement"))
      talk ! Dialog.PushMessageToTalk(Tester(1), "3")
      gate.expectMsg(Endpoint.DeliverMessageToUser(Tester(1), "Thank you!", Some(talk.hashCode())))
      gate.expectNoMsg()

      talk ! Dialog.PushMessageToTalk(Tester(2), "4")
      gate.expectMsg(Endpoint.AskEvaluationFromHuman(Tester(2), s"Please evaluate the breadth"))
      talk ! Dialog.PushMessageToTalk(Tester(2), "5")
      gate.expectMsg(Endpoint.AskEvaluationFromHuman(Tester(2), s"Please evaluate the engagement"))
      talk ! Dialog.PushMessageToTalk(Tester(2), "6")
      gate.expectMsg(Endpoint.DeliverMessageToUser(Tester(2), "Thank you!", Some(talk.hashCode())))

      gate.expectMsgPF(3.seconds) {
        case Endpoint.FinishTalkForUser(Tester(1), _) =>
        case Endpoint.FinishTalkForUser(Tester(2), _) =>
      }
      gate.expectMsgPF(3.seconds) {
        case Endpoint.FinishTalkForUser(Tester(1), _) =>
        case Endpoint.FinishTalkForUser(Tester(2), _) =>
      }
      gate.expectNoMsg()


      storage.expectMsg(MongoStorage.WriteDialog(talk.hashCode(), Set(Tester(1), Tester(2)), "test", Seq(Tester(1) -> "ololo"), Set((Tester(1),(1,2,3)), (Tester(2),(4,5,6)))))
    }
  }

  "human user after /test command" must {
    "see error if arguments is invalid" in {
      val gate = TestProbe()
      val storage = TestProbe()
      val daddy = system.actorOf(DialogFather.props(gate.ref, textGenerator, storage.ref))
      gate.expectMsg(Endpoint.SetDialogFather(daddy))
      daddy ! DialogFather.UserAvailable(Bot("1"))

      daddy ! DialogFather.CreateTestDialogWithBot(Tester(2), "2")
      gate.expectMsg(Endpoint.ChancelTestDialog(Tester(2), "Can not create a dialog."))
    }
  }
}
