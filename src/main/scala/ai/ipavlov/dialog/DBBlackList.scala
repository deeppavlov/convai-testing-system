package ai.ipavlov.dialog

import ai.ipavlov.communication.user.UserSummary
import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.pattern.AskSupport

import scala.concurrent.duration._
import scala.concurrent.Future

trait DBBlackList extends BlacklistSupport { self: Actor with ActorLogging with AskSupport =>
  val database: ActorRef

  def blacklist: Future[Set[UserSummary]] = database.ask(MongoStorage.GetBlackList)(3.seconds, context.self).mapTo[Set[UserSummary]]
}
