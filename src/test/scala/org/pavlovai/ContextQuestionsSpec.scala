package org.pavlovai

import org.pavlovai.dialog.ContextQuestions
import org.scalatest.WordSpec

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * @author vadim
  * @since 05.07.17
  */
class ContextQuestionsSpec extends WordSpec {
  import scala.concurrent.ExecutionContext.Implicits.global

  "randomLine" must {
    "return random string" in {
      val l1 = ContextQuestions.selectRandom
      val l2 = ContextQuestions.selectRandom

      assert(l1.isSuccess)
      assert(l2.isSuccess)
      assert(l1 !== l2)
    }
  }
}
