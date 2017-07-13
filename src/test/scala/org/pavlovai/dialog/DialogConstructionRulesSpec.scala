package org.pavlovai.dialog

import org.pavlovai.communication.User
import org.scalatest.{Matchers, WordSpecLike}

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/**
  * @author vadim
  * @since 07.07.17
  */
class DialogConstructionRulesSpec extends WordSpecLike with Matchers {
  import scala.concurrent.ExecutionContext.Implicits.global

  "A DialogConstructionRules availableDialogs" must {
    "return only correct pairs for user list" in {
      val constructor = new DialogConstructionRules {
        override protected val textGenerator: ContextQuestions = (ec: ExecutionContext) => Future.successful("test")
        override val availableUsers: mutable.Set[User] = mutable.Set(TestUser("1"), TestUser("2"))
      }

      for (i <- 1 to 1000) {
        val l = Await.result(constructor.availableDialogs, 1.second)
        assert(
          (l == List((TestUser("2"), TestUser("1"), "test"))) || l == List((TestUser("1"), TestUser("2"), "test"))
        )
      }
    }
  }

  case class TestUser(name: String) extends User
}
