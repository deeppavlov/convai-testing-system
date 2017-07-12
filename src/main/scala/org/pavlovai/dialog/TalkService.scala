package org.pavlovai.dialog

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import akka.util.Timeout
import org.pavlovai.Context
import org.pavlovai.user.{UserWithChat, ChatRepository}

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

  context.system.scheduler.schedule(1.second, 1.second)(self ! AssembleDialogs)

  override def receive: Receive = {
    case AssembleDialogs => assembleDialogs()
    case Terminated(t) => blockingResources.get(t).foreach(ul => userRepo ! ChatRepository.DeactivateChats(ul))



    case UserAvailable(user: UserWithChat) =>
    case UserUnavailable(user: UserWithChat) =>

  }

  private val blockingResources = mutable.Map[ActorRef, List[UserWithChat]]()

  private def createDialog(a: UserWithChat, b: UserWithChat, txt: String): Unit = {
    val t = context.actorOf(Talk.props(a, b, txt, gate))
    userRepo ! ChatRepository.AddHoldedChatsToTalk(List(a, b), t)
    blockingResources += t -> List(a, b)
    context.watch(t)
  }

  private def assembleDialogs() {
    (userRepo ? ChatRepository.HoldChats(2)).foreach {
      case (u1: UserWithChat) :: (u2: UserWithChat) :: Nil =>
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

  private case object AssembleDialogs

  case class UserAvailable(user: UserWithChat)
  case class UserUnavailable(user: UserWithChat)
}