package org.pavlovai.dialog

import java.time.Clock

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import org.pavlovai.communication._

import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.{Random, Try}
import scala.util.control.NonFatal

/**
  * @author vadim
  * @since 06.07.17
  */
class DialogFather(gate: ActorRef, protected val textGenerator: ContextQuestions, databaseDialogStorage: ActorRef, clck: Clock, val rnd: Random) extends Actor with ActorLogging with DialogConstructionRules {
  import DialogFather._
  private implicit val ec = context.dispatcher

  private val humanBotCoef: Double = Try(context.system.settings.config.getDouble("talk.bot.human_bot_coefficient")).getOrElse(0.5)

  private val availableUsers: mutable.Set[User] = mutable.Set.empty[User]
  private val usersChatsInTalks: mutable.Map[ActorRef, List[User]] = mutable.Map[ActorRef, List[User]]()

  gate ! Endpoint.SetDialogFather(self)

  context.system.scheduler.schedule(5.second, 5.second) { self ! AssembleDialogs }

  override def receive: Receive = {
    case AssembleDialogs =>
      availableDialogs(humanBotCoef)(availableUsers.toSet.diff(usersChatsInTalks.values.flatten.filter(_.isInstanceOf[Human]).toSet).toList).foreach(assembleDialog(databaseDialogStorage))

    case Terminated(t) =>
      usersChatsInTalks.remove(t).foreach { ul => log.info("dialog terminated, users {} leave from dialog", ul) }

    case UserAvailable(user: User) =>
      if (availableUsers.add(user)) {
        val mustBeChanged = usersChatsInTalks.filter { case (_, ul) => ul.contains(user) }.keySet
        mustBeChanged.foreach { k => usersChatsInTalks.get(k).map(_.filter(_ != user)).map(usersChatsInTalks.put(k, _)) }

        val dialRes = availableDialogs(humanBotCoef)(availableUsers.toSet.diff(usersChatsInTalks.values.flatten.filter(_.isInstanceOf[Human]).toSet).toList)
        dialRes.foreach(assembleDialog(databaseDialogStorage))
        if (user.isInstanceOf[Human] && !dialRes.foldLeft(Set.empty[User]) { case (s, (a, b, _)) => s + a + b }.contains(user)) {
          gate ! Endpoint.SystemNotificationToUser(user, "Please wait for your partner.")
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

    case CreateTestDialogWithBot(owner, botId) =>
      (for {
        txt <- textGenerator.selectRandom
        bot = Bot(botId)
        if !usersChatsInTalks.values.flatten.toSet.contains(owner)
        if availableUsers.contains(bot)
        _ = log.info("test dialog {}-{}", owner, bot)
        _ = assembleDialog(nopStorage)(owner, bot, txt)
      } yield ()).recover {
        case NonFatal(e) =>
          log.warning("can't create test dialog with bot: {}, error: {}", botId, e)
          gate ! Endpoint.ChancelTestDialog(owner, "Can not create a dialog.")
      }
  }

  private val nopStorage = context.actorOf(Props(new NopStorage), name="nop-storage")

  private def assembleDialog(storage: ActorRef)(available: (User, User, String)) = available match {
    case (a: Bot, b: Bot, _) => log.debug("bot-bot dialogs disabled, ignore pair {}-{}", a, b)
    case (a, b, txt) =>
      val dialog = context.actorOf(Dialog.props(a, b, txt, gate, storage, clck), name = s"dialog-${java.util.UUID.randomUUID()}")
      log.info("start talk between {} and {}", a, b)
      gate ! Endpoint.ActivateTalkForUser(a, dialog)
      gate ! Endpoint.ActivateTalkForUser(b, dialog)
      usersChatsInTalks += dialog -> List(a, b)

      context.watch(dialog)

      if (a.isInstanceOf[Human]) availableUsers.remove(a)
      if (b.isInstanceOf[Human]) availableUsers.remove(b)

      dialog ! Dialog.StartDialog
  }
}

object DialogFather {
  def props(gate: ActorRef, textGenerator: ContextQuestions, databaseDialogStorage: ActorRef, rnd: Random, clock: Clock) =
    Props(new DialogFather(gate, textGenerator, databaseDialogStorage, clock, rnd))

  private case object AssembleDialogs
  case class CreateTestDialogWithBot(user: Human, botId: String)

  case class UserAvailable(user: User)
  case class UserLeave(user: User)

  private class NopStorage extends Actor {
    override def receive: Receive = {
      case _ =>
    }
  }
}