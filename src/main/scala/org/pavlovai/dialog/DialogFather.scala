package org.pavlovai.dialog

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props, Terminated}
import akka.util.Timeout
import org.pavlovai.communication._

import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.Try

/**
  * @author vadim
  * @since 06.07.17
  */
class DialogFather(gate: ActorRef, protected val textGenerator: ContextQuestions) extends Actor with ActorLogging with akka.pattern.AskSupport with DialogConstructionRules {
  import DialogFather._
  private implicit val ec = context.dispatcher
  private implicit val timeout: Timeout = 5.seconds

  private val cooldownPeriod: FiniteDuration = Try(Duration.fromNanos(context.system.settings.config.getDuration("talk.bot.talk_period_min").toNanos)).getOrElse(1.minutes)

  private val availableUsers: mutable.Set[User] = mutable.Set.empty[User]
  private val cooldownBots: mutable.Map[Bot, Deadline] = mutable.Map.empty[Bot, Deadline]
  private val usersChatsInTalks: mutable.Map[ActorRef, List[User]] = mutable.Map[ActorRef, List[User]]()

  private val noobs: mutable.Set[Human] = mutable.Set.empty[Human]

  gate ! Endpoint.SetDialogFather(self)

  context.system.scheduler.schedule(1.second, 1.second) {
    self ! AssembleDialogs
    self ! CleanCooldownList
  }

  override def receive: Receive = {
    case AssembleDialogs =>
      availableDialogs(availableUsers.toSet, usersChatsInTalks.values.flatten.toSet ++ cooldownBots.keySet).foreach { case (a, b, txt) =>
        startDialog(a, b, txt)
      }
      noobs.foreach { noob =>
        gate ! Endpoint.DeliverMessageToUser(noob, "Sorry, wait for the opponent", None)
        noobs.remove(noob)
      }

    case Terminated(t) =>
      usersChatsInTalks.get(t).foreach { ul =>
        ul.foreach { user =>
          gate ! Endpoint.FinishTalkForUser(user, t)
          user match {
            case u: Human => noobs.add(u)
            case _ =>
          }
        }
        log.info("users {} leave from dialog", ul)
      }
      usersChatsInTalks.remove(t)

    case CleanCooldownList => cooldownBots.retain { case (_, deadline) => deadline.hasTimeLeft() }

    case UserAvailable(user: User) => userAvailable(user)
    case UserLeave(user: User) =>
      if(availableUsers.remove(user)) {
        log.info("user leave: {}, dialog killed", user)
        usersChatsInTalks.filter { case (_, users) => users.contains(user) }.foreach { case (k, v) =>
          k ! PoisonPill //TODO
        }
        user match {
          case u: Human =>
            noobs.remove(u)
            gate ! Endpoint.DeliverMessageToUser(user, "Bye", None)
          case _ =>
        }
      }
  }

  private def startDialog(a: User, b: User, txt: String): Unit = {
    val t = context.actorOf(Dialog.props(a, b, txt, gate), name = s"dialog-${java.util.UUID.randomUUID()}")
    log.info("start talk between {} and {}", a, b)
    if (a.id.hashCode < b.id.hashCode) {
      gate ! Endpoint.ActivateTalkForUser(a, t)
      gate ! Endpoint.ActivateTalkForUser(b, t)
      usersChatsInTalks += t -> List(a, b)
    } else {
      gate ! Endpoint.ActivateTalkForUser(b, t)
      gate ! Endpoint.ActivateTalkForUser(a, t)
      usersChatsInTalks += t -> List(b, a)
    }
    context.watch(t)
    userAddedToChat(a)
    userAddedToChat(b)

    t ! Dialog.StartDialog
  }

  private def userAvailable(user: User): Unit = {
    if (availableUsers.add(user)) {
      user match {
        case u: Human => noobs.add(u)
        case _ =>
      }
      log.debug("new user available: {}", user)
    }
  }

  private def userAddedToChat(user: User): Unit = {
    user match {
      case u: Human => noobs.remove(u)
      case u: Bot =>
        if(cooldownBots.put(u, cooldownPeriod.fromNow).isEmpty) log.info("bot {} go to sleep on {}", u, cooldownPeriod)
      case _ =>
    }
  }
}

object DialogFather {
  def props(gate: ActorRef, textGenerator: ContextQuestions) = Props(new DialogFather(gate, textGenerator))

  private case object AssembleDialogs
  private case object CleanCooldownList

  case class UserAvailable(user: User)
  case class UserLeave(user: User)
}