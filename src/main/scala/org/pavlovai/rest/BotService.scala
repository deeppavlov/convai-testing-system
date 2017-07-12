package org.pavlovai.rest

import java.time.Instant

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import info.mukel.telegrambot4s.models.{Chat, ChatType, Message, Update}
import org.pavlovai.user.{BotUserWithChat, ChatRepository, Gate}
import spray.json._

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.util.{Random, Try}
import scala.concurrent.duration._

/**
  * @author vadim
  * @since 10.07.17
  */
class BotService extends Actor with ActorLogging {
  import BotService._
  private implicit val ec: ExecutionContext = context.dispatcher

  context.system.scheduler.schedule(1.second, 1.second)(self ! CleanCooldownList)

  private val rnd: Random = Random

  private val cooldownPeriod = Try(Duration.fromNanos(context.system.settings.config.getDuration("bot.talk_period_min").toNanos)).getOrElse(1.minutes)

  private val botsQueues: Map[String, mutable.Queue[Update]] =
    Try(context.system.settings.config.getStringList("bot.registered").asScala).getOrElse(Seq.empty)
      .map(_ -> mutable.Queue.empty[Update]).toMap

  private val activeBots: mutable.Map[BotUserWithChat, ActorRef] = mutable.Map.empty[BotUserWithChat, ActorRef]

  override def receive: Receive = {
    case GetMessages(token) =>
      sender ! botsQueues.get(token).fold[Any] {
        log.warning("bot {} not registered", token)
        akka.actor.Status.Failure(new IllegalArgumentException("bot not registered"))
      }(_.toList)

    case SendMessage(token, chat, m: BotMessage) =>
      activeBots.get(BotUserWithChat(chat, token)).foreach(_ ! Gate.PushMessageToTalk(BotUserWithChat(chat, token), m.text))
      sender ! Message(rnd.nextInt(), None, Instant.now().getNano, Chat(chat, ChatType.Private), text = Some(m.toJson(botMessageFormat).toString))

    case Gate.DeliverMessageToUser(BotUserWithChat(chat_id, token), text) =>
      botsQueues.get(token).fold[Any] {
        log.warning("bot {} not registered", token)
        akka.actor.Status.Failure(new IllegalArgumentException("bot not registered"))
      }(_ += Update(0, Some(Message(0, None, Instant.now().getNano, Chat(chat_id, ChatType.Private), text = Some(text)))) )



    case ChatRepository.HoldChats(count: Int) =>
      @tailrec
      def selectBots(count: Int, acc: List[BotUserWithChat]): List[BotUserWithChat] = {
        if (count <= 0) acc
        else {
          selectBot() match {
            case None => List.empty[BotUserWithChat]
            case Some(bot) => selectBots(count - 1, bot :: acc)
          }
        }
      }
      sender ! selectBots(count, List.empty)

    case ChatRepository.AddHoldedChatsToTalk(chats: List[org.pavlovai.user.UserWithChat], dialog: ActorRef) =>
      chats.foreach {
        case chat: BotUserWithChat => activeBots += chat -> dialog
        case _ => log.warning("unrecognized chat type, ignored!")
      }

    case ChatRepository.DeactivateChats(user: List[org.pavlovai.user.UserWithChat]) =>
      user.foreach {
        case b: BotUserWithChat => activeBots -= b
        case _ => log.warning("unrecognized chat type, ignored!")
      }

    case CleanCooldownList => cooldownTokens.retain { case (_, deadline) => deadline.hasTimeLeft() }

  }

  private val cooldownTokens: mutable.Map[String, Deadline] = mutable.Map.empty[String, Deadline]

  private def selectBot(): Option[BotUserWithChat] = {
    rnd.shuffle(botsQueues.keySet.filterNot(cooldownTokens.contains)).headOption.map { token =>
      cooldownTokens += token -> cooldownPeriod.fromNow
      BotUserWithChat(rnd.nextLong(), token)
    }
  }

  private def availableBots: Int = botsQueues.size - cooldownTokens.size
}

object BotService extends SprayJsonSupport with DefaultJsonProtocol  {

  def props: Props = Props[BotService]

  sealed trait BotMessage {
    val text: String
  }
  case class TextMessage(text: String) extends BotMessage
  case class TextWithEvaluationMessage(text: String, evaluation: Int) extends BotMessage
  case class SummaryEvaluation(quality: Int, breadth: Int, engagement: Int)
  case class TalkEvaluationMessage(evaluation: SummaryEvaluation) extends BotMessage {
    val text = ""
  }

  case class SendMessage(token: String, chat_id: Long, text: BotMessage)
  case class GetMessages(token: String)

  implicit val normalMessageFormat: JsonFormat[TextWithEvaluationMessage] = jsonFormat2(TextWithEvaluationMessage)
  implicit val summaryEvaluationFormat: JsonFormat[SummaryEvaluation] = jsonFormat3(SummaryEvaluation)
  implicit val endMessageFormat: JsonFormat[TalkEvaluationMessage] = jsonFormat1(TalkEvaluationMessage)
  implicit val firstMessageFormat: JsonFormat[TextMessage] = jsonFormat1(TextMessage)
  implicit val botMessageFormat = new JsonFormat[BotMessage] {
    override def write(obj: BotMessage): JsValue = obj match {
      case TextMessage(text: String) => JsObject("text" -> text.toJson)
      case TextWithEvaluationMessage(text: String, evaluation: Int) => JsObject("text" -> text.toJson, "evaluation" -> evaluation.toJson)
      case TalkEvaluationMessage(SummaryEvaluation(quality, breadth, engagment)) => JsObject("quality" -> quality.toJson, "breadth" -> breadth.toJson, "engagement" -> engagment.toJson)
    }

    override def read(json: JsValue): BotMessage = json.asJsObject.getFields("text") match {
      case Seq(JsString("/end")) => json.convertTo[TalkEvaluationMessage]
      case _ if json.asJsObject.fields.contains("evaluation") => json.convertTo[TextWithEvaluationMessage]
      case _ if !json.asJsObject.fields.contains("evaluation") => json.convertTo[TextMessage]
      case _ => serializationError(s"Invalid json format: $json")
    }
  }

  implicit val sendMessageFormat: JsonFormat[SendMessage] = jsonFormat3(SendMessage)

  private case object CleanCooldownList
}
