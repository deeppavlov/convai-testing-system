package ai.ipavlov.dialog

import ai.ipavlov.communication.user.User
import ai.ipavlov.dialog.MongoStorage.BlackList
import akka.actor.{Actor, ActorRef}
import akka.pattern.AskSupport
import scala.concurrent.duration._

import scala.concurrent.Future

trait BlackListSupport { self: Actor with AskSupport =>
  val database: ActorRef
  implicit val timeout: akka.util.Timeout = 3.seconds

  def blacklist: Future[BlackList] = (database ? MongoStorage.GetBlackList).mapTo[BlackList]
}
