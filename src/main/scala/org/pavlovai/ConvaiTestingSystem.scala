package org.pavlovai

import akka.actor.{ActorSystem, PoisonPill}
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.Done
import akka.http.javadsl.server.PathMatchers
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger
import org.pavlovai.dialog.TalkService
import org.pavlovai.telegram.{Bot, TelegramService}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.StdIn
import scala.util.{Failure, Success, Try}

object ConvaiTestingSystem extends App {
  private val conf = ConfigFactory.load()
  private implicit val akkaSystem = ActorSystem("convai", conf)
  private implicit val materializer: ActorMaterializer = ActorMaterializer()
  private implicit val executionContext = akkaSystem.dispatcher
  private val logger = Logger(getClass)

  private val telegramService = akkaSystem.actorOf(TelegramService.props, "telegram-service")
  private val talkService = akkaSystem.actorOf(TalkService.props(telegramService), "talk-service")
  akkaSystem.scheduler.schedule(1.second, 1.second)(talkService ! TalkService.AssembleDialogs)

  val route =
    get {
      pathPrefix("api.telegram.org" / "\w+".r / "sendMessage") { token =>
        parameters("chat_id", "text") { (chat_id, text) =>
          complete(s"The color is '$chat_id' and the background is '$text'")
        }
      }
    } ~ get {
      pathPrefix("api.telegram.org" / "\w+".r / "getUpdates") { token =>
        complete("OK")
      }
    }


  private def setting(key: String): Try[String] = Try(conf.getString(key)).orElse {
    logger.error("No configuration for telegram.token found!")
    Failure(new RuntimeException("not configured"))
  }

  (setting("telegram.token"), setting("telegram.webhook")) match {
    case (Success(token), Success(webhook)) => new Bot(akkaSystem, materializer, telegramService, route, token, webhook).run()
    case _ => logger.error("telegram bot not started, because it not configured! Check config file.")
  }

  sys.addShutdownHook {
    telegramService ! PoisonPill
    talkService ! PoisonPill
    Await.ready(akkaSystem.terminate(), 30.seconds)
    logger.info("system shutting down")
  }
}


