package org.pavlovai.dialog

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import org.pavlovai.communication._

import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.Try

/**
  * @author vadim
  * @since 06.07.17
  */
class DialogFather(gate: ActorRef, protected val textGenerator: ContextQuestions, databaseDialogStorage: ActorRef) extends Actor with ActorLogging with DialogConstructionRules {
  import DialogFather._
  private implicit val ec = context.dispatcher

  private val cooldownPeriod: FiniteDuration = Try(Duration.fromNanos(context.system.settings.config.getDuration("talk.bot.talk_period_min").toNanos)).getOrElse(1.minutes)

  private val availableUsers: mutable.Set[User] = mutable.Set.empty[User]
  private val cooldownBots: mutable.Map[Bot, Deadline] = mutable.Map.empty[Bot, Deadline]
  private val usersChatsInTalks: mutable.Map[ActorRef, List[User]] = mutable.Map[ActorRef, List[User]]()

  gate ! Endpoint.SetDialogFather(self)

  context.system.scheduler.schedule(5.second, 5.second) { self ! AssembleDialogs }

  override def receive: Receive = {
    case AssembleDialogs =>
      cooldownBots.retain { case (_, deadline) => deadline.hasTimeLeft() }
      availableDialogs(availableUsers.toSet, (cooldownBots.keySet ++ usersChatsInTalks.values.flatten).toSet)
        .foreach(assembleDialog)

    case Terminated(t) =>
      usersChatsInTalks.remove(t).foreach { ul =>
        ul.foreach(user => gate ! Endpoint.FinishTalkForUser(user, t))
        log.info("dialog terminated, users {} leave from dialog", ul)
      }

    case UserAvailable(user: User) =>
      if (availableUsers.add(user)) {
        val dialRes = availableDialogs(availableUsers.toSet, (cooldownBots.keySet ++ usersChatsInTalks.values.flatten).toSet)
        dialRes.foreach(assembleDialog)
        if (!dialRes.foldLeft(Set.empty[User]) { case (s, (a, b, _)) => s + a + b}.contains(user)) {
          gate ! Endpoint.DeliverMessageToUser(user, "Please wait for your partner.", None)
        }

        log.debug("new user available: {}", user)
      }

    case UserLeave(user: User) =>
      if(availableUsers.remove(user)) log.info("user leave: {}", user)

      usersChatsInTalks.foreach {
        case (dialog, users) if users.contains(user) =>
          log.info("user {} leave, dialog {} finished", user, users)
          dialog ! Dialog.EndDialog
        case _ =>
      }
  }

  private def assembleDialog(available: (User, User, String)) = available match {
    case (a: Bot, b: Bot, _) => log.debug("bot-bot dialogs disabled, ignore pair {}-{}", a, b)
    case (a, b, txt) =>
      val dialog = context.actorOf(Dialog.props(a, b, txt, gate, databaseDialogStorage), name = s"dialog-${java.util.UUID.randomUUID()}")
      log.info("start talk between {} and {}", a, b)
      gate ! Endpoint.ActivateTalkForUser(a, dialog)
      gate ! Endpoint.ActivateTalkForUser(b, dialog)
      usersChatsInTalks += dialog -> List(a, b)

      context.watch(dialog)

      def userAddedToChat(user: User): Unit = user match {
        case u: Human => availableUsers.remove(u)
        case u: Bot => if (cooldownBots.put(u, cooldownPeriod.fromNow).isEmpty) log.info("bot {} go to sleep on {}", u, cooldownPeriod)
        case _ =>
      }

      userAddedToChat(a)
      userAddedToChat(b)

      dialog ! Dialog.StartDialog
  }
}

object DialogFather {
  def props(gate: ActorRef, textGenerator: ContextQuestions, databaseDialogStorage: ActorRef) = Props(new DialogFather(gate, textGenerator, databaseDialogStorage))

  private case object AssembleDialogs

  case class UserAvailable(user: User)
  case class UserLeave(user: User)
}