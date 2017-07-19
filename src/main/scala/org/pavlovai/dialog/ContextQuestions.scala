package org.pavlovai.dialog

import scala.util.{Failure, Success, Try}

/**
  * @author vadim
  * @since 05.07.17
  */

trait ContextQuestions {
  def selectRandom: Try[String]
}

object ContextQuestions extends ContextQuestions {
  private val rnd = scala.util.Random

  def selectRandom: Try[String] = {
    Try(io.Source.fromResource("context.txt").getLines.size).flatMap { size =>
      val dataset = io.Source.fromResource("context.txt")
      def goToIndex(rest: Int, it: Iterator[String]): Try[String] = {
        if (rest <= 1 && it.hasNext) Success(it.next())
        else if (it.hasNext) goToIndex(rest - 1, {it.next(); it})
        else Failure(new RuntimeException())
      }

      goToIndex(rnd.nextInt(size), dataset.getLines)
    }
  }
}
