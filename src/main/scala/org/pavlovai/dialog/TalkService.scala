package org.pavlovai.dialog

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import akka.util.Timeout
import org.pavlovai.{Context, User}
import org.pavlovai.telegram.TelegramService.{AddHoldedUsersToTalk, DeactivateUsers, HoldUsers}

import scala.collection.mutable
import scala.concurrent.duration._

/**
  * @author vadim
  * @since 06.07.17
  */
class TalkService(telegramUserRepo: ActorRef) extends Actor with ActorLogging with akka.pattern.AskSupport {
  import TalkService._
  private implicit val ec = context.dispatcher
  private implicit val timeout: Timeout = 5.seconds

  context.system.scheduler.schedule(1.second, 1.second, self, AssembleDialogs)

  override def receive: Receive = {
    case AssembleDialogs => assembleDialogs()
    case Terminated(t) => blockingResources.get(t).foreach(ul => telegramUserRepo ! DeactivateUsers(ul))
  }

  private val blockingResources = mutable.Map[ActorRef, List[User]]()

  private def assembleDialogs() {
    (telegramUserRepo ? HoldUsers(2)).foreach {
      case Some((u1: User) :: (u2: User) :: Nil) =>
        Context.selectRandom.foreach { txt =>
          val t = context.actorOf(Talk.props(u1, u2, txt, telegramUserRepo))
          telegramUserRepo ! AddHoldedUsersToTalk(List(u1, u2), t)
          blockingResources += t -> List(u1, u2)
          context.watch(t)
          assembleDialogs()
        }
      case Some(_) => log.error("not consistent state!")
      case None => log.debug("no collocutors found, wait")
    }
  }
}

object TalkService {
  def props(telegramUserRepo: ActorRef) = Props(new TalkService(telegramUserRepo))

  case object AssembleDialogs
}