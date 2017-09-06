package org.pavlovai.dialog

import java.io.File

import scala.util.{Failure, Success, Try}
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization

/**
  * @author vadim
  * @since 05.07.17
  */

trait ContextQuestions {
  def selectRandom: Try[String]
}

object SqadQuestions extends ContextQuestions {
  private val rnd = scala.util.Random

  def selectRandom: Try[String] = {
    Try(io.Source.fromResource("context.txt").getLines.size).flatMap { size =>
      val dataset = io.Source.fromResource("context.sqad.txt")
      def goToIndex(rest: Int, it: Iterator[String]): Try[String] = {
        if (rest <= 1 && it.hasNext) Success(it.next())
        else if (it.hasNext) goToIndex(rest - 1, {it.next(); it})
        else Failure(new RuntimeException())
      }

      goToIndex(rnd.nextInt(size), dataset.getLines)
    }
  }
}

object WikiNewsQuestions extends ContextQuestions {
  private val rnd = scala.util.Random
  private case class NewsItem(url: String, text: String)
  private implicit val formats = Serialization.formats(FullTypeHints(List(classOf[NewsItem])))
  private val l =
    Try {
      val l = parse(this.getClass.getClassLoader.getResourceAsStream("context.wikinews.json")).extract[List[NewsItem]]
      (l, l.size)
    }

  def selectRandom: Try[String] = {
    l.flatMap { case (data, size) =>
      data.lift(rnd.nextInt(size)).fold[Try[String]](throw new IllegalStateException()) { case NewsItem(url, text) =>
        Success(text + "\n" + "source: " + url)
      }
    }
  }
}
