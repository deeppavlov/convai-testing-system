package ai.ipavlov.dialog

import ai.ipavlov.communication.user.UserSummary

import scala.concurrent.Future

trait NoBlackList {
  def blacklist: Future[Set[UserSummary]] = Future.successful(Set.empty)
}
