package org.pavlovai.dialog

import akka.event.LoggingAdapter
import org.pavlovai.communication.{Bot, Human}
import org.scalatest.{Matchers, WordSpecLike}

import scala.util.{Random, Success, Try}

/**
  * @author vadim
  * @since 07.07.17
  */
class DialogConstructionRulesSpec extends WordSpecLike with Matchers {
  "A DialogConstructionRules availableDialogs" must {
    "return only correct pairs for user list with even length" in {
      val constructor = new DialogConstructionRules {
        override protected val textGenerator: ContextQuestions = new ContextQuestions {
          override def selectRandom: Try[String] = Success("test")
        }

        override val rnd: Random = scala.util.Random

        override def log: LoggingAdapter = ???
      }

      for (i <- 1 to 1000) {
        val l = constructor.availableDialogs(0.5)(List((Tester("1"), 1), (Tester("2"), 1)))
        assert(l === List((Tester("1"), Tester("2"), "test")))
      }

      for (i <- 1 to 1000) {
        val l = constructor.availableDialogs(0.5)(List((Tester("1"), 1), (Bot("2"), 1)))
        assert(l === List())
      }
    }

    "return only correct pairs for user list with not even length" in {
      val constructor = new DialogConstructionRules {
        override protected val textGenerator: ContextQuestions = new ContextQuestions {
          override def selectRandom: Try[String] = Success("test")
        }

        override def log: LoggingAdapter = ???

        override val rnd: Random = scala.util.Random
      }

      var hbDialogs = 0.0
      var hhDialogs = 0
      for (i <- 1 to 10000) {
        val l = constructor.availableDialogs(0.2)(List((Tester("1"), 1), (Tester("2"), 1), (Bot("3"), 100)))
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

  case class Tester(override val id: String) extends Human {
    override val chatId: Long = id.toLong
  }
}
