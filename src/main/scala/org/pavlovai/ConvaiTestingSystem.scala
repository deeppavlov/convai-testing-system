package org.pavlovai

import akka.actor.{ActorSystem, PoisonPill}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger
import org.pavlovai.communication.Endpoint
import org.pavlovai.dialog.{ContextQuestions, DialogFather, MongoStorage}

import scala.concurrent.Await
import scala.concurrent.duration._

object ConvaiTestingSystem extends App {
  private val conf = ConfigFactory.load()
  private implicit val akkaSystem = ActorSystem("convai", conf)
  private implicit val materializer: ActorMaterializer = ActorMaterializer()
  private implicit val executionContext = akkaSystem.dispatcher
  private val logger = Logger(getClass)

  private val gate = akkaSystem.actorOf(Endpoint.props, name = "communication-endpoint")
  private val mongoStorage = akkaSystem.actorOf(MongoStorage.props(), name="dialog-storage")
  private val talkConstructor = akkaSystem.actorOf(DialogFather.props(gate, ContextQuestions, mongoStorage), "talk-constructor")

  private implicit val timeout: Timeout = 5.seconds

  sys.addShutdownHook {
    talkConstructor ! PoisonPill
    Await.ready(akkaSystem.terminate(), 30.seconds)
    logger.info("system shutting down")
  }
}

