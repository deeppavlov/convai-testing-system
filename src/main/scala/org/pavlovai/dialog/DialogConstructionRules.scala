package org.pavlovai.dialog

import org.pavlovai.communication.{Bot, Human, TelegramChat, User}

import scala.collection.mutable
import scala.concurrent.duration.Deadline
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Random, Success, Try}

/**
  * @author vadim
  * @since 13.07.17
  */
trait DialogConstructionRules {
  protected val textGenerator: ContextQuestions

  private val rnd = Random

  def availableDialogs(usersList: Set[User], blackList: Set[User]): Seq[(User, User, String)] = {
    val users = rnd.shuffle(usersList.diff(blackList).toList)
    users.zip(users.reverse).take(users.size / 2).map { case (a, b) =>
      textGenerator.selectRandom.map { txt => (a, b, txt) }
    }.collect {
      case Success(d) => d
    }
  }
}
