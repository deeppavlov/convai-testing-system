package org.pavlovai.telegram

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Stash}
import info.mukel.telegrambot4s.api._
import info.mukel.telegrambot4s.methods.{ParseMode, SendMessage}
import info.mukel.telegrambot4s.models._
import org.pavlovai
import org.pavlovai.dialog.Talk

import scala.collection.mutable

/**
  * @author vadim
  * @since 04.07.17
  */
class TelegramService extends Actor with ActorLogging with Stash {
  import TelegramService._

  override def receive: Receive = unititialized

  private val unititialized: Receive = {
    case SetGateway(gate) =>
      context.become(initialized(gate))
      unstashAll()

    case m => stash()
  }

  private def initialized(request: RequestHandler): Receive = {
    case SetGateway(g) => context.become(initialized(g))

    case Command(chat, "/help") =>
      request(helpMessage(chat.id))

    case Command(Chat(id, ChatType.Private, _, username, _, _, _, _, _, _), "/begin") =>
      log.info("chatId {} ready to talk", username.getOrElse("unknown"))
      readyToTalk(id)
      request(SendMessage(Left(id), "*Please wait for your partner.*", Some(ParseMode.Markdown)))

    case Command(chat, "/end") =>
      log.info("chatId {} leave talk", chat.username.getOrElse("unknown"))
      leave(chat.id)

    case Command(chat, _) =>
      request(SendMessage(Left(chat.id), "Messages of this type aren't supported \uD83D\uDE1E"))

    case Update(num, Some(message), _, _, _, _, _, _, _, _) if isInDialog(message.chat.id) =>
      activeUsers.find { case (k, v) => k.id == message.chat.id && v.isDefined }.foreach {
        case (user, Some(dialog)) => message.text.foreach(dialog ! Talk.MessageFrom(user, _))
        case _ => throw new IllegalStateException("illegal state!")
      }

    //TODO: make this more beautiful
    case Update(num, Some(message), _, _, _, _, _, _, _, _) if isNotInDialog(message.chat.id) =>
      request(SendMessage(Left(message.chat.id), "*Please wait for your partner.*", Some(ParseMode.Markdown)))

    case Update(num, Some(message), _, _, _, _, _, _, _, _) => request(helpMessage(message.chat.id))

    case HoldUsers(count) =>
      def getUsers(count: Int, acc: List[pavlovai.User]): List[pavlovai.User] = {
        if (count == 0) acc
        else if (availableUsers.nonEmpty) {
          val id = availableUsers.toVector(scala.util.Random.nextInt(availableUsers.size))
          val newAcc = pavlovai.User(id) :: acc
          availableUsers -= id
          getUsers(count - 1, newAcc)
        } else {
          acc.foreach(u => availableUsers.add(u.id))
          List.empty
        }
      }

      if (availableUsers.size >= count) {
        val activated = getUsers(count, List.empty)
        activated.foreach(u => activeUsers += u -> None)
        if(activated.nonEmpty) Some(activated) else None
      } else None

    case AddHoldedUsersToTalk(users, dialog) =>
      if (activeUsers.forall { case (u, _) => users.contains(u) }) users.foreach { user =>
        activeUsers.get(user).foreach {
          case None => activeUsers += user -> Some(dialog)
          case _ => log.warning("attempt to establish talk with user in talk")
        }
      } else {
        log.error("user not in hold list")
        throw new IllegalStateException("user not in hold list")
      }

    case MessageTo(org.pavlovai.User(id), text) =>
      request(SendMessage(Left(id), text, Some(ParseMode.Markdown)))

    case DeactivateUsers(us) => us.foreach { case org.pavlovai.User(id) => readyToTalk(id) }
  }

  private val availableUsers = mutable.Set[Long]()
  private val activeUsers = mutable.Map[pavlovai.User, Option[ActorRef]]()

  private def readyToTalk(chatId: Long) = {
    availableUsers += chatId
    activeUsers.keys.find(_.id == chatId).foreach(activeUsers.remove)
  }

  private def leave(chatId: Long) = {
    availableUsers -= chatId
    activeUsers.keys.find(_.id == chatId).foreach(activeUsers.remove)
  }

  private def isInDialog(chatId: Long) = activeUsers.exists { case (k, _) => k.id == chatId }
  private def isNotInDialog(chatId: Long) = availableUsers.contains(chatId)

  private def helpMessage(chatId: Long) = SendMessage(Left(chatId),
    """
      |*Help message*
      |
      |Use:
      |
      |- /begin for start talk
      |- /end for end talk
      |- /help for help
      |
      |[link](http://vkurselife.com/wp-content/uploads/2016/05/b5789b.jpg)
    """.stripMargin, Some(ParseMode.Markdown))
}

object TelegramService {
  val props: Props = Props[TelegramService]

  private case object Command {
    def unapply(message: Update): Option[(Chat, String)] = {
      if (message.message.exists(_.text.exists(_.startsWith("/")))) Some((message.message.get.chat, message.message.get.text.get)) else None
    }
  }

  case class SetGateway(gate: RequestHandler)

  case class HoldUsers(count: Int)
  case class AddHoldedUsersToTalk(user: List[pavlovai.User], dialog: ActorRef)
  case class DeactivateUsers(user: List[pavlovai.User])

  case class MessageTo(user: pavlovai.User, text: String)
}
