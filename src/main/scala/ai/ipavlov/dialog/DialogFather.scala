package ai.ipavlov.dialog

import java.time.Clock

import ai.ipavlov.communication.user.{Bot, Human, UserSummary}
import ai.ipavlov.communication.Endpoint
import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}

import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.util.{Random, Try}

/**
  * @author vadim
  * @since 06.07.17
  */
class DialogFather(gate: ActorRef, protected val textGenerator: ContextQuestions, databaseDialogStorage: ActorRef, clck: Clock, val rnd: Random) extends Actor with ActorLogging with DialogConstructionRules {
  import DialogFather._
  private implicit val ec = context.dispatcher

  private val humanBotCoef: Double = Try(context.system.settings.config.getDouble("talk.bot.human_bot_coefficient")).getOrElse(1)

  private val availableUsers: mutable.Map[UserSummary, (Int, Int)] = mutable.Map.empty[UserSummary, (Int, Int)]
  private val usersChatsInTalks: mutable.Map[ActorRef, List[UserSummary]] = mutable.Map[ActorRef, List[UserSummary]]()
  private val usersDedline: mutable.Map[UserSummary, Deadline] = mutable.Map[UserSummary, Deadline]()

  gate ! Endpoint.SetDialogFather(self)

  context.system.scheduler.schedule(5.second, 5.second) { self ! AssembleDialogs }

  override def receive: Receive = {
    case AssembleDialogs =>
      availableDialogs(humanBotCoef)(availableUsersList).foreach(assembleDialog(databaseDialogStorage))

    case Terminated(t) =>
      usersChatsInTalks.remove(t).foreach { ul =>
        log.info("dialog terminated, users {} leave from dialog", ul)
        ul.foreach { u =>
          availableUsers.get(u).map { case (max, current) => availableUsers += u -> (max, current - 1)}
        }
      }

    case UserAvailable(user: UserSummary, maxConnections) =>
      if (!availableUsers.contains(user)) {
        availableUsers.put(user, (maxConnections, 0))
        if (user.isInstanceOf[Human]) usersDedline.put(user, Deadline.now + 10.seconds)
      }

      val mustBeChanged = usersChatsInTalks.filter { case (_, ul) => ul.contains(user) }.keySet
      mustBeChanged.foreach { k => usersChatsInTalks.get(k).map(_.filter(_ != user)).map(usersChatsInTalks.put(k, _)) }

      val dialRes = availableDialogs(humanBotCoef)(availableUsersList)
      dialRes.foreach(assembleDialog(databaseDialogStorage))
      if (user.isInstanceOf[Human] && !dialRes.foldLeft(Set.empty[UserSummary]) { case (s, (a, b, _)) => s + a + b }.contains(user)) {
        gate ! Endpoint.SystemNotificationToUser(user, "Please wait for your partner.")
      }

      log.debug("new user available: {}", user)
    case UserLeave(user: UserSummary) =>
      if(availableUsers.remove(user).isDefined) log.info("user leave: {}", user)

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

  private def assembleDialog(storage: ActorRef)(available: (UserSummary, UserSummary, String)) = available match {
    case (a: Bot, b: Bot, _) => log.debug("bot-bot dialogs disabled, ignore pair {}-{}", a, b)
    case (a, b, txt) =>
      val dialog = context.actorOf(Dialog.props(a, b, txt, gate, storage, clck), name = s"dialog-${java.util.UUID.randomUUID()}")
      gate ! Endpoint.ActivateTalkForUser(a, dialog)
      gate ! Endpoint.ActivateTalkForUser(b, dialog)
      usersChatsInTalks += dialog -> List(a, b)

      context.watch(dialog)

      availableUsers.get(a).map { case (max, current) => availableUsers += a -> (max, current + 1)}
      availableUsers.get(b).map { case (max, current) => availableUsers += b -> (max, current + 1)}

      //TODO
      usersDedline.remove(a)
      usersDedline.remove(b)
      if (a.isInstanceOf[Human]) availableUsers.remove(a)
      if (b.isInstanceOf[Human]) availableUsers.remove(b)

      dialog ! Dialog.StartDialog
  }

  private def availableUsersList: List[(UserSummary, Int, Deadline)] =
    availableUsers.filter { case (user, (maxConn, currentConn)) => currentConn < maxConn }.map { case (user, (maxConn, currentConn)) => user -> (maxConn - currentConn)}.toList.map { t =>
      (t._1, t._2, usersDedline.getOrElse(t._1, Deadline.now + 1.hour))
    }
}

object DialogFather {
  def props(gate: ActorRef, textGenerator: ContextQuestions, databaseDialogStorage: ActorRef, rnd: Random, clock: Clock) =
    Props(new DialogFather(gate, textGenerator, databaseDialogStorage, clock, rnd))

  private case object AssembleDialogs
  case class CreateTestDialogWithBot(user: Human, botId: String)

  case class UserAvailable(user: UserSummary, maxConnections: Int)
  case class UserLeave(user: UserSummary)

  private class NopStorage extends Actor {
    override def receive: Receive = {
      case _ =>
    }
  }
}