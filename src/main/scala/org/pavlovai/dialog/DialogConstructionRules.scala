package org.pavlovai.dialog

import akka.actor.ActorLogging
import org.pavlovai.communication.{Bot, Human, TelegramChat, User}

import scala.collection.mutable
import scala.concurrent.duration.Deadline
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Random, Success, Try}

/**
  * @author vadim
  * @since 13.07.17
  */
trait DialogConstructionRules { self: ActorLogging =>
  protected val textGenerator: ContextQuestions

  private val rnd = Random

  def availableDialogs(usersList: Set[User], blackList: Set[User]): Seq[(User, User, String)] = {
    val users = rnd.shuffle(usersList.diff(blackList).toList)
    users.zip(users.reverse).take(users.size / 2).map { case (u1, u2) =>
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
  }
}
