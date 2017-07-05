package org.pavlovai

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.pavlovai.telegram.Bot

import scala.util.{Failure, Success, Try}

object ConvaiTestingSystem extends App {
  private val conf = ConfigFactory.load()
  private implicit val akkaSystem = ActorSystem("convai-testing-system", conf)
  private val materializer: ActorMaterializer = ActorMaterializer()

  private def setting(key: String): Try[String] = Try(conf.getString(key)).orElse {
    System.err.println("No configuration for telegram.token found!")
    Failure(new RuntimeException("not configured"))
  }

  (setting("telegram.token"), setting("telegram.webhook")) match {
    case (Success(token), Success(webhook)) =>
      new Bot(akkaSystem, materializer, token, webhook).run()
    case _ => System.err.println("telegram bot not started, because it not configured! Check config file.")
  }
}


