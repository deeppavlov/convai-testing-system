package org.pavlovai.dialog

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import akka.util.Timeout
import org.pavlovai.communication._

import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.Try

/**
  * @author vadim
  * @since 06.07.17
  */
class DialogFather extends Actor with ActorLogging with akka.pattern.AskSupport with DialogConstructionRules {
  import DialogFather._
  private implicit val ec = context.dispatcher
  private implicit val timeout: Timeout = 5.seconds

  private val cooldownPeriod: FiniteDuration = Try(Duration.fromNanos(context.system.settings.config.getDuration("bot.talk_period_min").toNanos)).getOrElse(1.minutes)
  private val gate: ActorRef = context.actorOf(Endpoint.props(self), name = "communication-endpoint")

  val availableUsers: mutable.Set[User] = mutable.Set.empty[User]
  protected val textGenerator: ContextQuestions = ContextQuestions

  private val usersChatsInTalks: mutable.Map[ActorRef, List[User]] = mutable.Map[ActorRef, List[User]]()

  context.system.scheduler.schedule(1.second, 1.second) {
    self ! AssembleDialogs
    self ! CleanCooldownList
  }

  override def receive: Receive = {
    case AssembleDialogs => availableDialogs.foreach( _.foreach { case (a, b, txt) => startDialog(a, b, txt) } )

    case Terminated(t) =>
      usersChatsInTalks.get(t).foreach { ul =>
        ul.foreach { u =>
          gate ! Endpoint.RemoveTargetTalkForUserWithChat(u)
          u match {
            case u: TelegramChat => busyHumans -= u
            case _ =>
          }
        }

        log.info("users {} leave from dialog", ul)
      }
    case CleanCooldownList => cooldownBots.retain { case (_, deadline) => deadline.hasTimeLeft() }

    case UserAvailable(user: User) =>
      if (!availableUsers.add(user)) log.info("new user available: {}", user)
    case UserLeave(user: User) =>
      if(availableUsers.remove(user)) {
        log.info("user leave: {}, dialog killed", user)
        usersChatsInTalks.filter { case (_, users) => users.contains(user) }.keySet.foreach(_ ! Dialog.EndDialog)
      }
  }

  private def startDialog(a: User, b: User, txt: String): Unit = {
    val t = context.actorOf(Dialog.props(a, b, txt, gate))
    log.info("start talk between {} and {}", a, b)
    gate ! Endpoint.AddTargetTalkForUserWithChat(a, t)
    gate ! Endpoint.AddTargetTalkForUserWithChat(b, t)
    usersChatsInTalks += t -> List(a, b)
    context.watch(t)
    addToBlockLists(a)
    addToBlockLists(b)
  }

  private def addToBlockLists(a: User) {
    a match {
      case u: TelegramChat => busyHumans += u
      case u: Bot =>
        if(cooldownBots.put(u, cooldownPeriod.fromNow).isEmpty) log.info("bot {} go to sleep on {}", u, cooldownPeriod)
      case _ =>
    }
  }
}

object DialogFather {
  def props = Props(new DialogFather)

  private case object AssembleDialogs
  private case object CleanCooldownList

  case class UserAvailable(user: User)
  case class UserLeave(user: User)
}