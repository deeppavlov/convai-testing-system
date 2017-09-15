package ai.ipavlov.communication.telegram

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.Logger
import info.mukel.telegrambot4s.actors.ActorBroker
import info.mukel.telegrambot4s.api.declarative.{Callbacks, Commands}
import info.mukel.telegrambot4s.api.{BotBase, RequestHandler, Webhook}
import info.mukel.telegrambot4s.clients.AkkaClient

import scala.concurrent.ExecutionContext
import scala.util.Try

/**
  * @author vadim
  * @since 05.07.17
  */
class BotWorker(sys: ActorSystem,
                telegramUserRepo: ActorRef,
                externalRoutes: Route,
                override val token: String,
                override val webhookUrl: String
         )(implicit val materializer: ActorMaterializer) extends BotBase with Webhook with ActorBroker {
  implicit val executionContext: ExecutionContext = sys.dispatcher
  implicit val system: ActorSystem = sys
  val client: RequestHandler = new AkkaClient(token)
  val logger = Logger(getClass)

  override def webhookRoute: Route = externalRoutes ~ super.webhookRoute

  override val port: Int =  Try(system.settings.config.getInt("https.port")).toOption.fold{
    logger.warn("https.port not found, use port 8433")
    8433
  } { port =>
    logger.info(s"bind on $port port")
    port.toInt
  }

  override val broker: Option[ActorRef] = Some(telegramUserRepo)

  telegramUserRepo ! TelegramEndpoint.SetGateway(this.request)

}

