package org.pavlovai.telegram

import java.security.SecureRandom

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Stash}
import info.mukel.telegrambot4s.api._
import info.mukel.telegrambot4s.methods.{ParseMode, SendMessage}
import info.mukel.telegrambot4s.models._
import org.pavlovai.user.Gate.PushMessageToTalk
import org.pavlovai.user.{Gate, Human, UserRepository}

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

    case Command(chat, "/start") =>
      request(helpMessage(chat.id))

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
      activeUsers.find { case (k, v) => k.chat_id == message.chat.id && v.isDefined }.foreach {
        case (user, Some(dialog)) => message.text.foreach(dialog ! PushMessageToTalk(user, _))
        case _ => throw new IllegalStateException("illegal state!")
      }

    //TODO: make this more beautiful
    case Update(num, Some(message), _, _, _, _, _, _, _, _) if isNotInDialog(message.chat.id) =>
      request(SendMessage(Left(message.chat.id), "*Please wait for your partner.*", Some(ParseMode.Markdown)))

    case Update(num, Some(message), _, _, _, _, _, _, _, _) => request(helpMessage(message.chat.id))

    case UserRepository.HoldUsers(count) =>
      def getUsers(count: Int, acc: List[Human]): List[Human] = {
        if (count == 0) acc
        else if (availableUsers.nonEmpty) {
          val id = availableUsers.toVector(rnd.nextInt(availableUsers.size))
          val newAcc = Human(id) :: acc
          availableUsers -= id
          getUsers(count - 1, newAcc)
        } else {
          acc.foreach(u => availableUsers.add(u.chat_id))
          List.empty
        }
      }

      sender ! (if (availableUsers.size >= count) {
        val activated = getUsers(count, List.empty)
        activated.foreach(u => activeUsers += u -> None)
        activated
      } else List.empty)

    case UserRepository.AddHoldedUsersToTalk(users, dialog) =>
      if (activeUsers.forall {
        case (u: Human, _) => users.contains(u)
        case _ => false
      }) users.foreach { case user: Human =>
        activeUsers.get(user).foreach {
          case None => activeUsers += user -> Some(dialog)
          case _ => log.warning("attempt to establish talk with user in talk")
        }
      case _ => log.warning("adding not human, ignored!")
      } else {
        log.error("user not in hold list")
        throw new IllegalStateException("user not in hold list")
      }

    case Gate.DeliverMessageToUser(Human(id), text) =>
      request(SendMessage(Left(id), text, Some(ParseMode.Markdown)))

    case UserRepository.DeactivateUsers(us) => us.foreach {
      case Human(id) => readyToTalk(id)
      case _ => log.warning("deactivating not a human user, ignored!")
    }
  }

  private val availableUsers = mutable.Set[Long]()
  private val activeUsers = mutable.Map[Human, Option[ActorRef]]()

  private def readyToTalk(chatId: Long) = {
    availableUsers += chatId
    activeUsers.keys.find(_.chat_id == chatId).foreach(activeUsers.remove)
  }

  private def leave(chatId: Long) = {
    availableUsers -= chatId
    activeUsers.keys.find(_.chat_id == chatId).foreach(activeUsers.remove)
  }

  private def isInDialog(chatId: Long) = activeUsers.exists { case (k, _) => k.chat_id == chatId }
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

  private val rnd = scala.util.Random.javaRandomToRandom(new SecureRandom())
}

object TelegramService {
  val props: Props = Props[TelegramService]

  private case object Command {
    def unapply(message: Update): Option[(Chat, String)] = {
      if (message.message.exists(_.text.exists(_.startsWith("/")))) Some((message.message.get.chat, message.message.get.text.get)) else None
    }
  }

  case class SetGateway(gate: RequestHandler)
}
