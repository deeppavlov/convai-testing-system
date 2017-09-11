package ai.ipavlov.dialog

import ai.ipavlov.communication.{Bot, Human, User}
import akka.event.LoggingAdapter

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

  var urgentlyDistributedK = 0

  def availableDialogs(humanBotCoef: Double)(users: List[(User, Int, Deadline)]): Seq[(User, User, String)] = {
    val dedlinedUsers = users.filter(_._3.isOverdue()).map(t => (t._1, t._2))
    val commonUsers = users.filter(_._3.hasTimeLeft()).map(t => (t._1, t._2))

    val humans = rnd.shuffle(commonUsers.filter { case (user, _) => user.isInstanceOf[Human] }).map(_._1)
    val robots = mutable.Map(commonUsers.filter { case (user, _) => user.isInstanceOf[Bot] }: _*)
    val P0 = 1.0 - 1.0 / (2 * humanBotCoef + 1)

    def randomRobot(): Option[User] =
      rnd.shuffle(robots.filter(_._2 > 0)).headOption.map { case (r, count) =>
        robots.update(r, count - 1)
        r
      }

    val overdueUsers = dedlinedUsers.filter(d => d._2 > 0).foldRight(List.empty[(User, User)]) { case ((user, capacity), acc) =>
      if (!robots.exists(_._2 > 0) || capacity < 1) acc
      else randomRobot().fold[List[(User, User)]](acc) { r =>
        urgentlyDistributedK += 1
        (user, r) :: acc
      }
    }

    val humanProb = (P0 * Math.exp(urgentlyDistributedK)) / (1 + P0 * (Math.exp(urgentlyDistributedK) - 1))

    (humans.zip(humans.reverse).take(humans.length / 2)
      .map { case (u1, u2) => if (u1.id.hashCode < u2.id.hashCode) (u1, u2) else (u2, u1) }
      .foldRight(List.empty[(User, User)]) { case ((a, b), acc) =>
        val isHumanPair = rnd.nextDouble() < humanProb
        val robot = rnd.shuffle(robots.filter(_._2 > 0)).headOption

        if (isHumanPair && robot.isDefined) (a, b) :: acc
        else {
          (randomRobot(), randomRobot()) match {
            case (Some(r1), Some(r2)) => (a, r1) :: (b, r2) :: acc
            case (Some(r1), None) => (a, r1) :: acc
            case (None, Some(r1)) => (a, r1) :: acc
            case (None, None) => acc
          }
        }
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