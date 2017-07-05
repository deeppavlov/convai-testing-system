package org.pavlovai

import org.scalatest.WordSpec

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * @author vadim
  * @since 05.07.17
  */
class ContextDatasetSpec extends WordSpec {
  import scala.concurrent.ExecutionContext.Implicits.global

  "randomLine" must {
    "return random string" in {
      val l1 = Await.result(ContextDataset.randomLine, 15.seconds)
      val l2 = Await.result(ContextDataset.randomLine, 15.seconds)

      assert(l1.nonEmpty)
      assert(l2.nonEmpty)
      assert(l1 !== l2)
    }
  }
}
