package org.pavlovai.dialog

import akka.actor.{Actor, ActorLogging, Props}
import org.mongodb.scala._
import org.mongodb.scala.ObservableImplicits
import org.mongodb.scala.bson.ObjectId
import org.pavlovai.communication.User
import org.mongodb.scala.bson.codecs.Macros._
import org.mongodb.scala.bson.codecs.DEFAULT_CODEC_REGISTRY
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}

import scala.util.{Failure, Success, Try}

/**
  * @author vadim
  * @since 13.07.17
  */
class MongoStorage extends Actor with ActorLogging with ObservableImplicits {
  import MongoStorage._

  self ! Init

  override def receive: Receive = unitialized

  private def initialized(database: MongoDatabase): Receive = {
    case dialog: WriteDialog =>
      val dialogs: MongoCollection[MongoStorage.Dialog] = database.getCollection("dialogs")
      dialogs.insertOne(MongoStorage.Dialog(dialog)).toFuture.onComplete {
        case Failure(e) => log.error("dialog NOT saved: {}", e)
        case Success(v) => log.debug("saved, {}", v.toString())
      }
  }

  private def unitialized: Receive = {
    case Init =>
      Try(context.system.settings.config.getString("talk.logger.connection_string")).foreach { conStr =>
        context.become(initialized(MongoClient(conStr).getDatabase("datasets").withCodecRegistry(codecRegistry)))
      }
  }
}

object MongoStorage {
  def props() = Props(new MongoStorage)

  private case object Init

  private case class UserSummary(id: String, t: String)

  private case class DialogEvaluation(ownerId: String, quality: Int, breadth: Int, engagement: Int)

  private case class DialogThreadItem(userId: String, text: String)

  private case class Dialog(_id: ObjectId, dialogId: Int, users: Set[UserSummary], context: String, tread: Seq[DialogThreadItem], evaluation: Set[DialogEvaluation])
  private object Dialog {
    def apply(wd: WriteDialog): Dialog =
      new Dialog(new ObjectId(),
        wd.id,
        wd.users.map(u => UserSummary(u.id, u.getClass.getName)),
        wd.context, wd.tread.map { case (u, t) => DialogThreadItem(u.id, t) },
        wd.evaluation.map { case (u, (q, b, e)) => DialogEvaluation(u.id, q, b, e) } )
  }

  private val codecRegistry = fromRegistries(
    fromProviders(classOf[MongoStorage.UserSummary],
    classOf[MongoStorage.DialogThreadItem],
      classOf[MongoStorage.DialogEvaluation],
      classOf[MongoStorage.Dialog]), DEFAULT_CODEC_REGISTRY
  )

  case class WriteDialog(id: Int, users: Set[User], context: String, tread: Seq[(User, String)], evaluation: Set[(User, (Int, Int, Int))])
}

