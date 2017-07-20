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

  def availableDialogs(humanProb: Double)(usersList: Set[User], blackList: Set[User]): Seq[(User, User, String)] = {
    def getUsersPairs(users: List[User]): List[(User, User)] = users.zip(users.reverse).take(users.size / 2)
    def getUserBotPairs(users: Set[User], robots: List[User]): Set[(User, User)] = users.map((_, rnd.shuffle(robots).headOption)).collect {
      case p@(h, Some(b)) => (h, b)
    }

    val users = rnd.shuffle(usersList.diff(blackList).toList)
    val humans = users.filter(_.isInstanceOf[Human])
    val robots = users.filter(_.isInstanceOf[Bot])

    val users2user = humans.map( (_, rnd.nextDouble() < humanProb) ).collect { case (h, true) => h }
    val users2bot = humans.toSet.diff(users2user.toSet)

    (getUsersPairs(users2user) ++ getUserBotPairs(users2bot, robots))
      .map { case (u1, u2) =>
        val (a, b) =  if (u1.id.hashCode < u2.id.hashCode) (u1, u2) else (u2, u1)
        textGenerator.selectRandom.map { txt => (a, b, txt) }
      }
      .map {
        case f @ Failure(e) =>
          log.error("error on dialog construction: {}", e)
          f
        case m => m
      }
      .collect {
        case Success(d) => d
      }
      .map {
        case (u1: Bot, u2: Human, txt) => (u2, u1, txt)
        case other => other
      }
  }
}
