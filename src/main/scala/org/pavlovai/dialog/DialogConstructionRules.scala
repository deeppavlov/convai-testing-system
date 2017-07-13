package org.pavlovai.dialog

import org.pavlovai.communication.{Bot, TelegramChat, User}

import scala.collection.mutable
import scala.concurrent.duration.Deadline
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

/**
  * @author vadim
  * @since 13.07.17
  */
trait DialogConstructionRules {
  val availableUsers: mutable.Set[User]
  protected val busyHumans: mutable.Set[TelegramChat] = mutable.Set.empty[TelegramChat]
  protected val cooldownBots: mutable.Map[Bot, Deadline] = mutable.Map.empty[Bot, Deadline]
  protected val textGenerator: ContextQuestions

  private val rnd = Random

  def availableDialogs(implicit ec: ExecutionContext): Future[Seq[(User, User, String)]] = {
    val users = rnd.shuffle(availableUsers.diff(busyHumans.toSet ++ cooldownBots.keySet).toList)
    Future.sequence(users.zip(users.reverse).take(users.size / 2).map { case (a, b) =>
      textGenerator.selectRandom.map { txt => (a, b, txt) }
    })
  }
}
