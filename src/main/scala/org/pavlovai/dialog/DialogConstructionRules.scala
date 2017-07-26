package org.pavlovai.dialog

import akka.event.LoggingAdapter
import org.pavlovai.communication.{Bot, Human, User}

import scala.collection.mutable
import scala.concurrent.duration.Deadline
import scala.util.{Failure, Random, Success}

/**
  * @author vadim
  * @since 13.07.17
  */
trait DialogConstructionRules {
  protected val textGenerator: ContextQuestions

  val rnd: Random
  def log: LoggingAdapter

  /*def availableDialogs(humanBotCoef: Double)(users: List[(User, Int)]): Seq[(User, User, String)] = {
    val humans = rnd.shuffle(users.filter { case (user, _) => user.isInstanceOf[Human] }).map(_._1)
    val robots = mutable.Map(users.filter { case (user, _) => user.isInstanceOf[Bot] }: _*)
    val humanProb = 1.0 - 1.0 / (2 * humanBotCoef + 1)

    humans.zip(humans.reverse).take(humans.length / 2)
      .map { case (u1, u2) => if (u1.id.hashCode < u2.id.hashCode) (u1, u2) else (u2, u1) }
      .foldRight(List.empty[(User, User)]) { case ((a, b), acc) =>
        val isHumanPair = rnd.nextDouble() < humanProb
        def randomRobot = {
          val (r, count) = rnd.shuffle(robots.filter(_._2 > 0)).head
          robots.update(r, count - 1)
          r
        }
        if (isHumanPair || robots.isEmpty || (robots.size == 1 && robots.forall(_._2 == 1))) (a, b) :: acc
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
  }*/

  var urgentlyDistributedK = 0

  def availableDialogs(humanBotCoef: Double)(users: List[(User, Int, Deadline)]): Seq[(User, User, String)] = {
    val dedlinedUsers = users.filter(_._3.isOverdue()).map(t => (t._1, t._2))
    val commonUsers = users.filter(_._3.hasTimeLeft()).map(t => (t._1, t._2))

    val humans = rnd.shuffle(commonUsers.filter { case (user, _) => user.isInstanceOf[Human] }).map(_._1)
    val robots = mutable.Map(commonUsers.filter { case (user, _) => user.isInstanceOf[Bot] }: _*)
    val P0 = 1.0 - 1.0 / (2 * humanBotCoef + 1)

    def randomRobot() = {
      val (r, count) = rnd.shuffle(robots.filter(_._2 > 0)).head
      robots.update(r, count - 1)
      r
    }

    val overdueUsers = dedlinedUsers.filter(d => d._2 > 0).foldRight(List.empty[(User, User)]) { case ((user, capacity), acc) =>
      if (!robots.exists(_._2 > 0) || capacity < 1) acc
      else {
        urgentlyDistributedK += 1
        (user, randomRobot()) :: acc
      }
    }

    val humanProb = (P0 * Math.exp(urgentlyDistributedK)) / (1 + P0 * (Math.exp(urgentlyDistributedK) - 1))

    (humans.zip(humans.reverse).take(humans.length / 2)
      .map { case (u1, u2) => if (u1.id.hashCode < u2.id.hashCode) (u1, u2) else (u2, u1) }
      .foldRight(List.empty[(User, User)]) { case ((a, b), acc) =>
        val isHumanPair = rnd.nextDouble() < humanProb

        if (isHumanPair || !robots.exists(_._2 > 0) || (robots.size == 1 && robots.forall(_._2 == 1))) (a, b) :: acc
        else (a, randomRobot()) :: (b, randomRobot()) :: acc
      } ++ overdueUsers)
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