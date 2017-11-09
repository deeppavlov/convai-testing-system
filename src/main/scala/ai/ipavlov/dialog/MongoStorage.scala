package ai.ipavlov.dialog

import java.time.Instant

import ai.ipavlov.communication.user._
import akka.actor.{Actor, ActorLogging, Props}
import akka.pattern.PipeToSupport
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.bson.codecs.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala.bson.codecs.Macros._
import org.mongodb.scala.{ObservableImplicits, _}

import scala.util.{Failure, Success, Try}

/**
  * @author vadim
  * @since 13.07.17
  */
class MongoStorage extends Actor with ActorLogging with ObservableImplicits with PipeToSupport {
  import MongoStorage._
  import context.dispatcher

  self ! Init

  override def receive: Receive = {
    log.info("initializing storage")
    unitialized
  }

  private def initialized(database: MongoDatabase): Receive = {
    case dialog: WriteDialog =>
      log.debug("saving dialog {}", dialog)
      val dialogs: MongoCollection[MongoStorage.Dialog] = database.getCollection("dialogs")
      dialogs.insertOne(MongoStorage.Dialog(dialog)).toFuture.onComplete {
        case Failure(e) => log.error("dialog didn't save: {}", e)
        case Success(v) => log.debug("saved, {}", v.toString())
      }

    case a: WriteLanguageAssessment =>
      val assessments: MongoCollection[MongoStorage.WriteLanguageAssessmentDTO] = database.getCollection("assessments")
      assessments.insertOne(WriteLanguageAssessmentDTO(a)).toFuture.onComplete {
        case Failure(e) => log.error("assessment didn't save: {}", e)
        case Success(v) => log.debug("assessments saved, {}", v.toString())
      }

    case GetBlackList =>
      val blacklist: MongoCollection[UserSummaryDTO] = database.getCollection("blacklist")
      blacklist.find().toFuture().map(_.map(dto2user).toSet).pipeTo(sender())

    case m: Complain =>
      val complains: MongoCollection[Complain] = database.getCollection("complains")
      complains.insertOne(m).toFuture.onComplete {
        case Failure(e) => log.error("complain didn't save: {}", e)
        case Success(v) => log.debug("complain saved, {}", v.toString())
      }
  }

  private def unitialized: Receive = {
    case Init =>
      Try(context.system.settings.config.getString("talk.logger.connection_string"))
        .recoverWith {
          case e =>
            log.error("storage didn't initialize: {}", e)
            Failure(e)
        }
        .foreach { conStr =>
          val dbName = conStr.split("/").last
          context.become(initialized(MongoClient(conStr).getDatabase(dbName).withCodecRegistry(codecRegistry)))
          log.info("storage was initialize")
        }
  }
}

object MongoStorage {
  def props() = Props(new MongoStorage)

  private case object Init

  private case class UserSummaryDTO(id: String, userType: String, username: String)

  private val dto2user: PartialFunction[UserSummaryDTO, UserSummary] = {
    case UserSummaryDTO(address, "org.pavlovai.communication.Bot", _) => Bot(address)
    case UserSummaryDTO(address, "org.pavlovai.communication.TelegramChat", username) => TelegramChat(address, username)
    case UserSummaryDTO(address, "org.pavlovai.communication.FbChat", username) => FbChat(address, username)
  }

  private case class DialogEvaluation(userId: String, quality: Int, breadth: Int, engagement: Int)

  private case class DialogThreadItem(userId: String, text: String, time: Int, evaluation: Int)

  private case class Dialog(_id: ObjectId, dialogId: Int, users: Set[UserSummaryDTO], context: String, thread: Seq[DialogThreadItem], evaluation: Set[DialogEvaluation])
  private object Dialog {
    def apply(wd: WriteDialog): Dialog =
      new Dialog(new ObjectId(),
        wd.id,
        wd.users.map {
          case u: Bot => UserSummaryDTO(u.address, u.getClass.getName, u.address)
          case u: Human => UserSummaryDTO(u.address, u.getClass.getName, u.username)
        },
        wd.context, wd.thread.map { case (u, txt, evaluation) => DialogThreadItem(u.address, txt, Instant.now().getNano, evaluation) },
        wd.evaluation.map { case (u, (q, b, e)) => DialogEvaluation(u.address, q, b, e) } )
  }

  private val codecRegistry = fromRegistries(
    fromProviders(classOf[MongoStorage.UserSummaryDTO],
    classOf[MongoStorage.DialogThreadItem],
      classOf[MongoStorage.DialogEvaluation],
      classOf[MongoStorage.Dialog],
      classOf[MongoStorage.WriteLanguageAssessmentDTO]
    ), DEFAULT_CODEC_REGISTRY
  )

  case class WriteDialog(id: Int, users: Set[UserSummary], context: String, thread: Seq[(UserSummary, String, Int)], evaluation: Set[(UserSummary, (Int, Int, Int))])

  private case class WriteLanguageAssessmentDTO(_id: ObjectId, login: Option[String], chatId: Long, level: Int)
  private object WriteLanguageAssessmentDTO {
    def apply(a: WriteLanguageAssessment): WriteLanguageAssessmentDTO = WriteLanguageAssessmentDTO(new ObjectId(), a.login, a.chatId, a.level)
  }

  case class WriteLanguageAssessment(login: Option[String], chatId: Long, level: Int)

  case object GetBlackList

  case class Complain(from: UserSummary, to: UserSummary, dialogId: Int)
}

