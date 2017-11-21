package ai.ipavlov

import java.time.Clock

import ai.ipavlov.communication.Endpoint
import ai.ipavlov.dialog.{DialogFather, MongoStorage, SquadQuestions, WikiNewsQuestions}
import akka.actor.{ActorSystem, PoisonPill}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger
import sun.misc.{Signal, SignalHandler}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

object ConvaiTestingSystem extends App {
  private val conf = ConfigFactory.load()
  private implicit val akkaSystem = ActorSystem("convai", conf)
  private implicit val materializer: ActorMaterializer = ActorMaterializer()
  private implicit val executionContext = akkaSystem.dispatcher
  private val logger = Logger(getClass)
  private val rnd = util.Random.javaRandomToRandom(new java.util.Random())

  private val mongoStorage = akkaSystem.actorOf(MongoStorage.props(), name="dialog-storage")
  private val gate = akkaSystem.actorOf(Endpoint.props(mongoStorage), name = "communication-endpoint")

  private val contextDataset = Try(conf.getString("talk.context.type")).getOrElse("squad") match {
    case "wikinews" => WikiNewsQuestions
    case _ => SquadQuestions
  }

  private val talkConstructor = akkaSystem.actorOf(DialogFather.props(gate, contextDataset, mongoStorage, rnd, Clock.systemDefaultZone()), "talk-constructor")

  private implicit val timeout: Timeout = 5.seconds

  sys.addShutdownHook {
    talkConstructor ! PoisonPill
    mongoStorage ! PoisonPill
    gate ! PoisonPill
    Await.ready(akkaSystem.terminate(), 30.seconds)
    logger.info("system shutting down")
  }

  Signal.handle(new Signal("SIGHUP"), (sig: Signal) => {
    gate ! Endpoint.Configure
  })
}
