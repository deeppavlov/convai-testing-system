package org.pavlovai.dialog

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import akka.util.Timeout
import org.pavlovai.Context
import org.pavlovai.user.{User, UserRepository}

import scala.collection.mutable
import scala.concurrent.duration._

/**
  * @author vadim
  * @since 06.07.17
  */
class TalkService(userRepo: ActorRef, gate: ActorRef) extends Actor with ActorLogging with akka.pattern.AskSupport {
  import TalkService._
  private implicit val ec = context.dispatcher
  private implicit val timeout: Timeout = 5.seconds

  override def receive: Receive = {
    case AssembleDialogs => assembleDialogs()
    case Terminated(t) => blockingResources.get(t).foreach(ul => userRepo ! UserRepository.DeactivateUsers(ul))
  }

  private val blockingResources = mutable.Map[ActorRef, List[User]]()

  private def createDialog(a: User, b: User, txt: String): Unit = {
    val t = context.actorOf(Talk.props(a, b, txt, gate))
    userRepo ! UserRepository.AddHoldedUsersToTalk(List(a, b), t)
    blockingResources += t -> List(a, b)
    context.watch(t)
  }

  private def assembleDialogs() {
    (userRepo ? UserRepository.HoldUsers(2)).foreach {
      case (u1: User) :: (u2: User) :: Nil =>
        Context.selectRandom.foreach { txt =>
          createDialog(u1, u2, txt)
          assembleDialogs()
        }
      case Nil => log.debug("no collocutors found, wait")
      case m => log.error(s"unrecognized response from user service: 2 users requested, get $m")
    }
  }
}

object TalkService {
  def props(userService: ActorRef, gate: ActorRef) = Props(new TalkService(userService, gate))

  case object AssembleDialogs
}