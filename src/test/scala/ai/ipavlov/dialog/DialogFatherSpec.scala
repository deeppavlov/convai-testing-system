package ai.ipavlov.dialog

import java.time.{Clock, Instant, ZoneId}
import java.util.Random

import ai.ipavlov.communication.{Bot, Endpoint, Human}
import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
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
    |    human_bot_coefficient = 1
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

  class FakeClock extends Clock {
    private var ticks: Int = 0

    override def withZone(zone: ZoneId): Clock = this

    override def getZone: ZoneId = ZoneId.systemDefault()

    override def instant(): Instant = Instant.ofEpochMilli(ticks)

    def tick(): Unit = ticks += 1
  }

  case class FakeRandom(bits: Int, double: Double) extends Random {
    override protected def next(bits: Int): Int = bits

    override def nextDouble(): Double = double
  }

  val rnd: util.Random = scala.util.Random.javaRandomToRandom(FakeRandom(0, 1))

  case class Tester(chatId: Long) extends Human

  "human user after /begin command" must {
    "see 'Please wait for your partner.' message if no opponent fond" in {
      val gate = TestProbe()
      val storage = TestProbe()
      val daddy = system.actorOf(DialogFather.props(gate.ref, textGenerator, storage.ref, rnd, new FakeClock))
      gate.expectMsg(Endpoint.SetDialogFather(daddy))

      daddy ! DialogFather.UserAvailable(Tester(5), 1)
      gate.expectMsg(Endpoint.SystemNotificationToUser(Tester(5), "Please wait for your partner."))
      gate.expectNoMsg()
    }

    "see dialog context if opponent found" in {
      val gate = TestProbe()
      val storage = TestProbe()
      val clck = new FakeClock
      val daddy = system.actorOf(DialogFather.props(gate.ref, textGenerator, storage.ref, rnd, clck))
      gate.expectMsg(Endpoint.SetDialogFather(daddy))
      daddy ! DialogFather.UserAvailable(Tester(1), 1)
      gate.expectMsg(Endpoint.SystemNotificationToUser(Tester(1), "Please wait for your partner."))
      daddy ! DialogFather.UserAvailable(Bot("1"), 1)
      daddy ! DialogFather.UserAvailable(Tester(2), 1)
      val t: ActorRef = gate.expectMsgPF(3.seconds) { case Endpoint.ActivateTalkForUser(Tester(1), tr) => tr }
      gate.expectMsg(Endpoint.ActivateTalkForUser(Bot("1"), t))

      gate.expectMsgPF(3.seconds) {
        case Endpoint.SystemNotificationToUser(Tester(2), "Please wait for your partner.") =>
        case Endpoint.SystemNotificationToUser(Tester(1), "test") =>
      }
      gate.expectMsgPF(3.seconds) {
        case Endpoint.SystemNotificationToUser(Tester(2), "Please wait for your partner.") =>
        case Endpoint.SystemNotificationToUser(Tester(1), "test") =>
      }
      gate.expectMsgPF(3.seconds) { case Endpoint.ChatMessageToUser(Bot("1"), "/start test", _, _) => }

      clck.tick()
      gate.expectNoMsg()
    }

    "see own and opponent messages" in pending

    "evaluate dialog when other user finish dialog" in {
      val gate = TestProbe()
      val storage = TestProbe()
      val clck = new FakeClock
      val daddy = system.actorOf(DialogFather.props(gate.ref, textGenerator, storage.ref, rnd, clck))
      gate.expectMsg(Endpoint.SetDialogFather(daddy))
      daddy ! DialogFather.UserAvailable(Tester(1), 1)
      gate.expectMsg(Endpoint.SystemNotificationToUser(Tester(1), "Please wait for your partner."))
      daddy ! DialogFather.UserAvailable(Tester(2), 1)
      gate.expectMsg(Endpoint.SystemNotificationToUser(Tester(2), "Please wait for your partner."))
      daddy ! DialogFather.UserAvailable(Bot("111"), 1)
      val talk: ActorRef = gate.expectMsgPF(3.seconds) { case Endpoint.ActivateTalkForUser(Tester(1), tr) => tr }
      gate.expectMsg(Endpoint.ActivateTalkForUser(Bot("111"), talk))

      gate.expectMsg(Endpoint.SystemNotificationToUser(Tester(1), "test"))
      gate.expectMsgPF(3.seconds) { case Endpoint.ChatMessageToUser(Bot("111"), "/start test", _, _) => }

      talk ! Dialog.PushMessageToTalk(Tester(1), "ololo")
      gate.expectMsg(Endpoint.ChatMessageToUser(Bot("111"), "ololo", talk.hashCode(), 0))

      clck.tick()

      daddy ! DialogFather.UserLeave(Tester(1))

      gate.expectMsgPF(3.seconds) {
        case Endpoint.AskEvaluationFromHuman(Tester(1), "Chat is finished, please evaluate the overall quality") =>
        case Endpoint.ChatMessageToUser(Bot("111"), "/end", _, _) =>
      }
      gate.expectMsgPF(3.seconds) {
        case Endpoint.AskEvaluationFromHuman(Tester(1), "Chat is finished, please evaluate the overall quality") =>
        case Endpoint.ChatMessageToUser(Bot("111"), "/end", _, _) =>
      }

      talk ! Dialog.PushMessageToTalk(Tester(1), "1")
      gate.expectMsg(Endpoint.AskEvaluationFromHuman(Tester(1), s"Please evaluate the breadth"))
      talk ! Dialog.PushMessageToTalk(Tester(1), "2")
      gate.expectMsg(Endpoint.AskEvaluationFromHuman(Tester(1), s"Please evaluate the engagement"))
      talk ! Dialog.PushMessageToTalk(Tester(1), "3")
      gate.expectMsg(Endpoint.EndHumanDialog(Tester(1), "Thank you! It was great! Please choose /begin to continue evaluation."))
      gate.expectMsg(Endpoint.FinishTalkForUser(Tester(1), talk))
      gate.expectNoMsg()

      storage.expectMsg(MongoStorage.WriteDialog(talk.hashCode(), Set(Tester(1), Bot("111")), "test", Seq((Tester(1), "ololo", 0)), Set((Tester(1),(1,2,3)), (Bot("111"),(0,0,0)))))
    }
  }

  "human user after /test command" must {
    "see error if arguments is invalid" in {
      val gate = TestProbe()
      val storage = TestProbe()
      val clck = new FakeClock
      val daddy = system.actorOf(DialogFather.props(gate.ref, textGenerator, storage.ref, rnd, clck))
      gate.expectMsg(Endpoint.SetDialogFather(daddy))
      daddy ! DialogFather.UserAvailable(Bot("1"), 1)

      daddy ! DialogFather.CreateTestDialogWithBot(Tester(2), "2")
      gate.expectMsg(Endpoint.ChancelTestDialog(Tester(2), "Can not create a dialog."))
    }
  }

  "talk construction" must {
    "be available for two users and one bot" in {
      val gate = TestProbe()
      val storage = TestProbe()
      val clck = new FakeClock
      val rnd: util.Random = scala.util.Random.javaRandomToRandom(FakeRandom(5, 1))
      val daddy = system.actorOf(DialogFather.props(gate.ref, textGenerator, storage.ref, rnd, clck))
      gate.expectMsg(Endpoint.SetDialogFather(daddy))
      daddy ! DialogFather.UserAvailable(Tester(1), 1)
      gate.expectMsg(Endpoint.SystemNotificationToUser(Tester(1), "Please wait for your partner."))
      daddy ! DialogFather.UserAvailable(Bot("1"), 1)
      daddy ! DialogFather.UserAvailable(Tester(2), 1)

      val talk: ActorRef = gate.expectMsgPF(3.seconds) { case Endpoint.ActivateTalkForUser(Tester(1), tr) => tr }
      gate.expectMsgPF(3.seconds) {
        case Endpoint.ActivateTalkForUser(Bot("1"), _) =>
        case Endpoint.SystemNotificationToUser(Tester(2), "Please wait for your partner.") =>
        case Endpoint.SystemNotificationToUser(Tester(1), "test") =>
        case Endpoint.ChatMessageToUser(Bot("1"), "/start test", _, _) =>
      }
      gate.expectMsgPF(3.seconds) {
        case Endpoint.ActivateTalkForUser(Bot("1"), _) =>
        case Endpoint.SystemNotificationToUser(Tester(2), "Please wait for your partner.") =>
        case Endpoint.SystemNotificationToUser(Tester(1), "test") =>
        case Endpoint.ChatMessageToUser(Bot("1"), "/start test", _, _) =>
      }
      gate.expectMsgPF(3.seconds) {
        case Endpoint.ActivateTalkForUser(Bot("1"), _) =>
        case Endpoint.SystemNotificationToUser(Tester(2), "Please wait for your partner.") =>
        case Endpoint.SystemNotificationToUser(Tester(1), "test") =>
        case Endpoint.ChatMessageToUser(Bot("1"), "/start test", _, _) =>
      }
      gate.expectMsgPF(3.seconds) {
        case Endpoint.ActivateTalkForUser(Bot("1"), _) =>
        case Endpoint.SystemNotificationToUser(Tester(2), "Please wait for your partner.") =>
        case Endpoint.SystemNotificationToUser(Tester(1), "test") =>
        case Endpoint.ChatMessageToUser(Bot("1"), "/start test", _, _) =>
      }
    }
  }
}
