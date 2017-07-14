package org.pavlovai.dialog

import org.pavlovai.communication.User
import org.scalatest.{Matchers, WordSpecLike}

import scala.collection.mutable
import scala.util.{Success, Try}

/**
  * @author vadim
  * @since 07.07.17
  */
class DialogConstructionRulesSpec extends WordSpecLike with Matchers {
  import scala.concurrent.ExecutionContext.Implicits.global

  "A DialogConstructionRules availableDialogs" must {
    "return only correct pairs for user list with even length" in {
      val constructor = new DialogConstructionRules {
        override protected val textGenerator: ContextQuestions = new ContextQuestions {
          override def selectRandom: Try[String] = Success("test")
        }
      }

      for (i <- 1 to 1000) {
        val l = constructor.availableDialogs(Set(TestUser("1"), TestUser("2")), Set())
        assert(
          (l == List((TestUser("2"), TestUser("1"), "test"))) || l == List((TestUser("1"), TestUser("2"), "test"))
        )
      }
    }

    "return only correct pairs for user list with not even length" in {
      val constructor = new DialogConstructionRules {
        override protected val textGenerator: ContextQuestions = new ContextQuestions {
          override def selectRandom: Try[String] = Success("test")
        }
      }

      for (i <- 1 to 1000) {
        val l = constructor.availableDialogs(Set(TestUser("1"), TestUser("2"), TestUser("3")), Set())
        assert(
          l == List((TestUser("2"), TestUser("1"), "test")) ||
            l == List((TestUser("1"), TestUser("2"), "test")) ||
            l == List((TestUser("1"), TestUser("3"), "test")) ||
            l == List((TestUser("3"), TestUser("1"), "test")) ||
            l == List((TestUser("2"), TestUser("3"), "test")) ||
            l == List((TestUser("3"), TestUser("2"), "test"))
        )
      }
    }
  }

  case class TestUser(id: String) extends User
}
