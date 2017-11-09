package ai.ipavlov.dialog

import ai.ipavlov.communication.user.UserSummary

import scala.concurrent.Future

trait BlacklistSupport {
  def blacklist: Future[Set[UserSummary]]
}
