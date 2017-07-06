package org.pavlovai.dialog

import java.security.SecureRandom

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
  * @author vadim
  * @since 05.07.17
  */
object Context {
  private val rnd = scala.util.Random.javaRandomToRandom(new SecureRandom())

  def selectRandom(implicit ec: ExecutionContext): Future[String] = {
    Future(io.Source.fromResource("context.txt").getLines.size).flatMap { size =>
      val dataset = io.Source.fromResource("context.txt")
      def goToIndex(rest: Int, it: Iterator[String]): Try[String] = {
        if (rest <= 1 && it.hasNext) Success(it.next())
        else if (it.hasNext) goToIndex(rest - 1, {it.next(); it})
        else Failure(new RuntimeException())
      }

      Future.fromTry(goToIndex(rnd.nextInt(size), dataset.getLines))
    }
  }
}
