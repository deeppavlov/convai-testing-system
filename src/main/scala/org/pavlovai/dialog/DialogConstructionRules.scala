package org.pavlovai.dialog

import akka.event.LoggingAdapter
import org.pavlovai.communication.{Bot, Human, User}

import scala.util.{Failure, Random, Success}

/**
  * @author vadim
  * @since 13.07.17
  */
trait DialogConstructionRules {
  protected val textGenerator: ContextQuestions

  val rnd: Random
  def log: LoggingAdapter

  def availableDialogs(humanBotCoef: Double)(users: List[User]): Seq[(User, User, String)] = {
    val humans = rnd.shuffle(users.filter(_.isInstanceOf[Human]))
    val robots = users.filter(_.isInstanceOf[Bot])
    val humanProb = 1.0 - 1.0 / (2 * humanBotCoef + 1)

    humans.zip(humans.reverse).take(humans.length / 2)
      .map { case (u1, u2) => if (u1.id.hashCode < u2.id.hashCode) (u1, u2) else (u2, u1) }
      .foldRight(List.empty[(User, User)]) { case ((a, b), acc) =>
        val isHumanPair = rnd.nextDouble() < humanProb
        def randomRobot = rnd.shuffle(robots).head
        if (isHumanPair || robots.isEmpty) (a, b) :: acc
        else (a, randomRobot) :: (b, randomRobot) :: acc
      }
      .map { case (a, b) =>
        textGenerator.selectRandom match {
          case f @ Failure(e) =>
            log.error("error on context text generation in dialog construction: {}, pair ({},{}) ignored", e, a, b)
            (a, b, f)
          case s => (a, b, s)
        }
      }.collect { case (a, b, Success(txt)) => (a, b, txt) }
  }
}