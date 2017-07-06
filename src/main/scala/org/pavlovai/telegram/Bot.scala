package org.pavlovai.telegram

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.Logger
import info.mukel.telegrambot4s.actors.ActorBroker
import info.mukel.telegrambot4s.api.declarative.Commands
import info.mukel.telegrambot4s.api.{BotBase, RequestHandler, TelegramBot, Webhook}
import info.mukel.telegrambot4s.clients.AkkaClient

import scala.concurrent.ExecutionContext

/**
  * @author vadim
  * @since 05.07.17
  */
class Bot(sys: ActorSystem,
          mat: ActorMaterializer,
          telegramUserRepo: ActorRef,
          override val token: String,
          override val webhookUrl: String
         ) extends BotBase with Webhook with ActorBroker {
  implicit val executionContext: ExecutionContext = sys.dispatcher
  implicit val system: ActorSystem = sys
  implicit val materializer: ActorMaterializer = mat
  val client: RequestHandler = new AkkaClient(token)
  val logger = Logger(getClass)

  override val port: Int = Option(System.getenv("PORT")).fold{
    logger.warn("PORT env variable not found, use port 8080")
    8080
  } { port =>
    logger.info(s"bind on $port port")
    port.toInt
  }

  override val broker: Option[ActorRef] = Some(telegramUserRepo)

  telegramUserRepo ! TelegramService.SetGateway(this.request)
}

