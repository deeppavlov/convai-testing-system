package org.pavlovai.communication

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.stream.ActorMaterializer
import org.pavlovai.communication.rest.{BotEndpoint, Routes}
import org.pavlovai.communication.telegram.{BotWorker, TelegramEndpoint}

import scala.util.Try

/**
  * @author vadim
  * @since 11.07.17
  */
class Endpoint(talkConstructor: ActorRef) extends Actor with ActorLogging {
  import Endpoint._

  private val telegramGate = context.actorOf(TelegramEndpoint.props(talkConstructor), "telegram-gate")
  private val botGate = context.actorOf(BotEndpoint.props(talkConstructor), "bot-gate")

  //TODO
  private val routerBotToken = Try(context.system.settings.config.getString("telegram.token")).getOrElse("unknown")
  private val webhook = Try(context.system.settings.config.getString("telegram.webhook")).getOrElse {
    log.error("telegram.webhook not set!")
    "https://localhost"
  }

  implicit val mat: ActorMaterializer = ActorMaterializer()

  private val bot = new BotWorker(context.system, telegramGate, Routes.route(botGate), routerBotToken, webhook).run()

  override def receive: Receive = {
    case message @ DeliverMessageToUser(_: TelegramChat, _, _) => telegramGate forward message
    case message @ DeliverMessageToUser(_: Bot, _, _) => botGate forward message

    case m @ AddTargetTalkForUserWithChat(_: TelegramChat, _) => telegramGate forward m
    case m @ AddTargetTalkForUserWithChat(_: Bot, _) => botGate forward m

    case m @ RemoveTargetTalkForUserWithChat(_: TelegramChat) => telegramGate forward m
    case m @ RemoveTargetTalkForUserWithChat(_: Bot) => botGate forward m
  }
}

object Endpoint {
  def props(talkConstructor: ActorRef): Props = Props(new Endpoint(talkConstructor))

  case class DeliverMessageToUser(receiver: User, message: String, fromDialogId: Int)

  case class AddTargetTalkForUserWithChat(user: User, talk: ActorRef)
  case class RemoveTargetTalkForUserWithChat(user: User)
}

