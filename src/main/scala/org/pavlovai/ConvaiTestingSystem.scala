package org.pavlovai

import akka.actor.{ActorSystem, PoisonPill}
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger
import org.pavlovai.dialog.TalkService
import org.pavlovai.telegram.{Bot, TelegramService}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

object ConvaiTestingSystem extends App {
  import scala.concurrent.ExecutionContext.Implicits.global

  private val conf = ConfigFactory.load()
  private implicit val akkaSystem = ActorSystem("convai", conf)
  private val materializer: ActorMaterializer = ActorMaterializer()
  private val logger = Logger(getClass)

  private val telegramService = akkaSystem.actorOf(TelegramService.props, "telegram-service")
  private val talkService = akkaSystem.actorOf(TalkService.props(telegramService), "talk-service")
  akkaSystem.scheduler.schedule(1.second, 1.second)(talkService ! TalkService.AssembleDialogs)

  private def setting(key: String): Try[String] = Try(conf.getString(key)).orElse {
    logger.error("No configuration for telegram.token found!")
    Failure(new RuntimeException("not configured"))
  }

  (setting("telegram.token"), setting("telegram.webhook")) match {
    case (Success(token), Success(webhook)) => new Bot(akkaSystem, materializer, telegramService, token, webhook).run()
    case _ => logger.error("telegram bot not started, because it not configured! Check config file.")
  }

  sys.addShutdownHook {
    telegramService ! PoisonPill
    talkService ! PoisonPill
    Await.ready(akkaSystem.terminate(), 30.seconds)
    logger.info("system shutting down")
  }
}


