package ai.ipavlov

import java.time.Clock

import ai.ipavlov.communication.Endpoint
import ai.ipavlov.communication.rest.BotEndpoint
import ai.ipavlov.communication.user.{Bot, Human, TelegramChat}
import ai.ipavlov.dialog.{DialogFather, SqadQuestions}
import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.collection.mutable
import scala.concurrent.duration._

/**
  * @author vadim
  * @since 07.09.17
  */
class RoutingIntegrationSpec extends TestKit(ActorSystem("BotEndpointSpec", ConfigFactory.parseString(
  """
    |bot {
    |  registered = [
    |    { token: "0A36119D-E6C0-4022-962F-5B5BDF21FD97", max_connections: 1, delayOn: true },
    |    { token: "1A36119D-E6C0-4022-962F-5B5BDF21FD97", max_connections: 1, delayOn: true }
    |  ]
    |}
    |
    |talk {
    |  talk_timeout = 10 minutes
    |  talk_length_max = 1000
    |  bot {
    |    human_bot_coefficient = 0.5
    |    delay {
    |      mean_k = 0.5
    |      variance = 5
    |    }
    |  }
    |
    |  context {
    |    type = "wikinews"
    |  }
    |}
  """.stripMargin))) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {

  "routing" must {
    "correct route dialogs to bots with 1 maximum available connections" in {

      var human_dialogs = 0
      var robot_dialogs = 0
      for (i <- 1 to 10) {
        val storage = TestProbe()
        val gate = TestProbe()
        val botGate = system.actorOf(BotEndpoint.props(gate.ref), "bot-gate"+i)
        val talkConstructor = system.actorOf(DialogFather.props(gate.ref, SqadQuestions, storage.ref, scala.util.Random, Clock.systemDefaultZone()), "talk-constructor" + i)
        var dialogConstructor: ActorRef = null
        val botActivationMessages = mutable.ArrayBuffer[DialogFather.UserAvailable]()
        gate.expectMsgPF(3.seconds) {
          case Endpoint.SetDialogFather(daddy) => dialogConstructor = daddy
          case m@DialogFather.UserAvailable(_: Bot, _) => botActivationMessages += m
        }
        gate.expectMsgPF(3.seconds) {
          case Endpoint.SetDialogFather(daddy) => dialogConstructor = daddy
          case m@DialogFather.UserAvailable(_: Bot, _) => botActivationMessages += m
        }
        gate.expectMsgPF(3.seconds) {
          case Endpoint.SetDialogFather(daddy) => dialogConstructor = daddy
          case m@DialogFather.UserAvailable(_: Bot, _) => botActivationMessages += m
        }
        botActivationMessages.foreach(dialogConstructor ! _)
        dialogConstructor ! DialogFather.UserAvailable(TelegramChat("1", "vasya"), 1)
        dialogConstructor ! DialogFather.UserAvailable(TelegramChat("2", "petya"), 1)

        gate.expectMsgPF(3.seconds) { case Endpoint.SystemNotificationToUser(_, _) => }
        gate.expectMsgPF(3.seconds) {
          case Endpoint.ActivateTalkForUser(_: Human, _) => human_dialogs += 1
          case Endpoint.ActivateTalkForUser(_: Bot, _) => robot_dialogs += 1
        }
        gate.expectMsgPF(3.seconds) {
          case Endpoint.ActivateTalkForUser(_: Human, _) => human_dialogs += 1
          case Endpoint.ActivateTalkForUser(_: Bot, _) => robot_dialogs += 1
        }

        dialogConstructor ! DialogFather.UserLeave(TelegramChat("1", "vasya"))
        dialogConstructor ! DialogFather.UserLeave(TelegramChat("1", "petya"))

        system.stop(botGate)
        system.stop(dialogConstructor)
        system.stop(talkConstructor)
      }

      assert(robot_dialogs > 0)
    }
  }

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }
}
