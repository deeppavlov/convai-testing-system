package org.pavlovai

import akka.actor.{ActorSystem, PoisonPill}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger
import org.pavlovai.dialog.TalkService
import org.pavlovai.rest.{BotService, Routes}
import org.pavlovai.telegram.{Bot, TelegramService}
import org.pavlovai.user.CumulativeGate

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

object ConvaiTestingSystem extends App {
  private val conf = ConfigFactory.load()
  private implicit val akkaSystem = ActorSystem("convai", conf)
  private implicit val materializer: ActorMaterializer = ActorMaterializer()
  private implicit val executionContext = akkaSystem.dispatcher
  private val logger = Logger(getClass)

  private val telegramService = akkaSystem.actorOf(TelegramService.props, "telegram-service")
  private val botService = akkaSystem.actorOf(BotService.props, "bot-service")
  private val apiGate = akkaSystem.actorOf(CumulativeGate.props(botService, telegramService), "api-gate-service")
  private val talkService = akkaSystem.actorOf(TalkService.props(telegramService, apiGate), "talk-service")

  akkaSystem.scheduler.schedule(1.second, 1.second)(talkService ! TalkService.AssembleDialogs)

  private implicit val timeout: Timeout = 5.seconds

  private def setting(key: String): Try[String] = Try(conf.getString(key)).orElse {
    logger.error("No configuration for telegram.token found!")
    Failure(new RuntimeException("not configured"))
  }

  (setting("telegram.token"), setting("telegram.webhook")) match {
    case (Success(token), Success(webhook)) => new Bot(akkaSystem, materializer, telegramService, Routes.route(botService, logger), token, webhook).run()
    case _ => logger.error("telegram bot not started, because it not configured! Check config file.")
  }

  sys.addShutdownHook {
    telegramService ! PoisonPill
    talkService ! PoisonPill
    Await.ready(akkaSystem.terminate(), 30.seconds)
    logger.info("system shutting down")
  }
}

