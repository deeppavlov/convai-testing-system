package ai.ipavlov

import ai.ipavlov.dialog.{SquadQuestions, WikiNewsQuestions}
import org.scalatest.WordSpec

/**
  * @author vadim
  * @since 05.07.17
  */
class ContextQuestionsSpec extends WordSpec {

  "SqadQuestions selectRandom" must {
    "return random string" in {
      val l1 = SquadQuestions.selectRandom
      val l2 = SquadQuestions.selectRandom

      assert(l1.isSuccess)
      assert(l2.isSuccess)
      assert(l1 !== l2)
    }
  }

  "ContextQuestions selectRandom" must {
    "return random string" in {
      val l1 = WikiNewsQuestions.selectRandom
      val l2 = WikiNewsQuestions.selectRandom

      assert(l1.isSuccess)
      assert(l2.isSuccess)
      assert(l1 !== l2)
    }
  }
}
