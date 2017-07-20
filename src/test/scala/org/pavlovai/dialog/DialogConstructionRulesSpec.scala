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
        val l = constructor.availableDialogs(0.5)(Set(Tester("1"), Bot("1")), Set())
        assert(
          (l == List((Tester("1"), Bot("1"), "test"))) ||
            (l == List())
        )
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

      for (i <- 1 to 1000) {
        val l = constructor.availableDialogs(0.5)(Set(Tester("1"), Tester("2"), Bot("3")), Set())
        assert(
          l == List((Tester("2"), Tester("1"), "test")) ||
            l == List((Tester("1"), Tester("2"), "test")) ||
            l == List((Tester("1"), Bot("3"), "test")) ||
            l == List((Tester("2"), Bot("3"), "test")) ||
            l == List((Tester("2"), Bot("3"), "test"), (Tester("1"), Bot("3"), "test")) ||
            l == List((Tester("1"), Bot("3"), "test"), (Tester("2"), Bot("3"), "test"))
        )
      }
    }
  }

  case class Tester(override val id: String) extends Human {
    override val chatId: Long = id.toLong
  }
}
