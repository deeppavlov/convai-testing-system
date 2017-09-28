package ai.ipavlov.dialog

import ai.ipavlov.communication.user.{Bot, Human}
import akka.event.LoggingAdapter
import org.scalatest.{Matchers, WordSpecLike}

import scala.concurrent.duration._
import scala.util.{Random, Success, Try}

/**
  * @author vadim
  * @since 07.07.17
  */
class DialogConstructionRulesSpec extends WordSpecLike with Matchers {
  private val deadline10sec = Deadline.now + 10.seconds

  "A DialogConstructionRules availableDialogs" must {
    val constructor = new DialogConstructionRules {
      override protected val textGenerator: ContextQuestions = new ContextQuestions {
        override def selectRandom: Try[String] = Success("test")
      }

      override val rnd: Random = scala.util.Random

      override def log: LoggingAdapter = ???
    }

    "return empty list if minimum 2 users and one bot not available" in {
      val l1 = constructor.availableDialogs(0.5)(List((Tester("1"), 1, deadline10sec), (Tester("2"), 1, deadline10sec)))
      assert(l1 === List())

      val l2 = constructor.availableDialogs(0.5)(List((Tester("1"), 1, deadline10sec), (Bot("1"), 1, deadline10sec)))
      assert(l2 === List())
    }

    "return only correct pairs for user list with not even length" in {
      var hbDialogs = 0.0
      var hhDialogs = 0
      for (i <- 1 to 10000) {
        val l = constructor.availableDialogs(0.2)(List((Tester("1"), 1, deadline10sec), (Tester("2"), 1, deadline10sec), (Bot("3"), 100, deadline10sec)))
        hbDialogs = hbDialogs + (if (l == List((Tester("1"), Bot("3"), "test"), (Tester("2"), Bot("3"), "test"))) 2 else 0)
        hhDialogs = hhDialogs + (if (l == List((Tester("1"), Tester("2"), "test"))) 1 else 0)
        assert(
            l == List((Tester("1"), Tester("2"), "test")) ||
            l == List((Tester("1"), Bot("3"), "test"), (Tester("2"), Bot("3"), "test"))
        )
      }
      val humanBotsCoef = hhDialogs / hbDialogs
      assert( 0.18 < humanBotsCoef && humanBotsCoef < 0.22)
    }
  }

  case class Tester(id: String) extends Human
}
