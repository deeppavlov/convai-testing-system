package ai.ipavlov.dialog

import java.time.Instant

import ai.ipavlov.communication.{Bot, TelegramChat, User}
import akka.actor.{Actor, ActorLogging, Props}
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.mongodb.scala.{ObservableImplicits, _}
import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.bson.codecs.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala.bson.codecs.Macros._

import scala.util.{Failure, Success, Try}

/**
  * @author vadim
  * @since 13.07.17
  */
class MongoStorage extends Actor with ActorLogging with ObservableImplicits {
  import MongoStorage._
  import context.dispatcher

  self ! Init

  override def receive: Receive = unitialized

  private def initialized(database: MongoDatabase): Receive = {
    case dialog: WriteDialog =>
      log.debug("saving dialog {}", dialog)
      val dialogs: MongoCollection[MongoStorage.Dialog] = database.getCollection("dialogs")
      dialogs.insertOne(MongoStorage.Dialog(dialog)).toFuture.onComplete {
        case Failure(e) => log.error("dialog NOT saved: {}", e)
        case Success(v) => log.debug("saved, {}", v.toString())
      }

    case a: WriteLanguageAssessment =>
      val assessments: MongoCollection[MongoStorage.WriteLanguageAssessmentDTO] = database.getCollection("assessments")
      assessments.insertOne(WriteLanguageAssessmentDTO(a)).toFuture.onComplete {
        case Failure(e) => log.error("assessment NOT saved: {}", e)
        case Success(v) => log.debug("assessments saved, {}", v.toString())
      }
  }

  private def unitialized: Receive = {
    case Init =>
      Try(context.system.settings.config.getString("talk.logger.connection_string")).foreach { conStr =>
        val dbName = conStr.split("/").last
        context.become(initialized(MongoClient(conStr).getDatabase(dbName).withCodecRegistry(codecRegistry)))
      }
  }
}

object MongoStorage {
  def props() = Props(new MongoStorage)

  private case object Init

  private case class UserSummary(id: String, userType: String, username: Option[String])

  private case class DialogEvaluation(userId: String, quality: Int, breadth: Int, engagement: Int)

  private case class DialogThreadItem(userId: String, text: String, time: Int, evaluation: Int)

  private case class Dialog(_id: ObjectId, dialogId: Int, users: Set[UserSummary], context: String, thread: Seq[DialogThreadItem], evaluation: Set[DialogEvaluation])
  private object Dialog {
    def apply(wd: WriteDialog): Dialog =
      new Dialog(new ObjectId(),
        wd.id,
        wd.users.map {
          case u: Bot => UserSummary(u.id, u.getClass.getName, Some(u.id))
          case u: TelegramChat => UserSummary(u.id, u.getClass.getName, u.username)
        },
        wd.context, wd.thread.map { case (u, txt, evaluation) => DialogThreadItem(u.id, txt, Instant.now().getNano, evaluation) },
        wd.evaluation.map { case (u, (q, b, e)) => DialogEvaluation(u.id, q, b, e) } )
  }

  private val codecRegistry = fromRegistries(
    fromProviders(classOf[MongoStorage.UserSummary],
    classOf[MongoStorage.DialogThreadItem],
      classOf[MongoStorage.DialogEvaluation],
      classOf[MongoStorage.Dialog],
      classOf[MongoStorage.WriteLanguageAssessmentDTO]
    ), DEFAULT_CODEC_REGISTRY
  )

  case class WriteDialog(id: Int, users: Set[User], context: String, thread: Seq[(User, String, Int)], evaluation: Set[(User, (Int, Int, Int))])

  private case class WriteLanguageAssessmentDTO(_id: ObjectId, login: Option[String], chatId: Long, level: Int)
  private object WriteLanguageAssessmentDTO {
    def apply(a: WriteLanguageAssessment): WriteLanguageAssessmentDTO = WriteLanguageAssessmentDTO(new ObjectId(), a.login, a.chatId, a.level)
  }

  case class WriteLanguageAssessment(login: Option[String], chatId: Long, level: Int)
}

