package ai.ipavlov.dialog

import ai.ipavlov.communication.user.UserSummary

import scala.concurrent.duration.Deadline

trait ConstructionRules {
  def availableDialogs(humanBotCoef: Double)(users: List[(UserSummary, Int, Deadline)]): Seq[(UserSummary, UserSummary, String)]
}
