package jp.co.soramitsu.nakayoshi

import jp.co.soramitsu.nakayoshi.Types._
import jp.co.soramitsu.nakayoshi.internals.Loggable

import akka.Done
import akka.actor.{Actor, ActorRef, ActorSystem, Timers}
import akka.pattern.pipe
import akka.stream.Materializer
import akka.stream.scaladsl._
import akka.util.ByteString
import org.json4s._
import org.json4s.native.JsonMethods.{render, compact}
import org.json4s.native.JsonParser.parse
import org.json4s.native.Serialization.read
import com.softwaremill.sttp._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import scala.concurrent.duration._

case class GtInnerMessage(text: String, id: String)
case class GtInnerUser(id: String, displayName: String, url: String)

class BotGitter(token: String)(
    implicit actorSystem: ActorSystem,
    implicit val sttpBackend: SttpBackend[Future, Source[ByteString, Any]],
    implicit val materializer: Materializer
) extends Actor with Loggable with Timers {

  private var router: ActorRef                        = _
  private var sink: Sink[MsgFromGitter, Future[Done]] = Sink.ignore
  private var selfId: Option[String]                  = None

  private implicit val executionContext: ExecutionContext = context.dispatcher
  private implicit val formats: Formats                   = DefaultFormats

  private val streamDelimeter    = ByteString(10)
  private val maximumFrameLength = 8192

  private val commonHeaders = Seq(
    "Accept"        -> "application/json",
    "Authorization" -> s"Bearer $token"
  )

  private val roomsUrl                     = uri"https://api.gitter.im/v1/rooms"
  private val selfUrl                      = uri"https://api.gitter.im/v1/user"
  private def messagesUrl(room: String)    = uri"https://api.gitter.im/v1/rooms/$room/chatMessages"
  private def messagesStream(room: String) = uri"https://stream.gitter.im/v1/rooms/$room/chatMessages"

  private def sendMsg(room: String, msg: String): Future[MsgGt] = {
    val headers  = commonHeaders :+ ("Content-Type" -> "application/json")
    val bodyJson = JObject("text" -> JString(msg))
    val body     = compact(render(bodyJson))
    sttp
      .headers(headers: _*)
      .body(body)
      .post(messagesUrl(room))
      .send()
      .map(_.body)
      .collect { case Right(v) => read[GtInnerMessage](v).id }
  }

  private def getRooms(): Future[List[GitterRoom]]                                 =
    sttp
      .headers(commonHeaders: _*)
      .get(roomsUrl)
      .send()
      .map(_.body)
      .collect { case Right(body) => read[List[GitterRoom]](body) }

  private def getMessages(room: String): Future[Response[Source[ByteString, Any]]] =
    sttp.headers(commonHeaders: _*).response(asStream[Source[ByteString, Any]]).get(messagesStream(room)).send()

  private def parseMessage(id: String, body: String): Option[MsgFromGitter] = {
    parse(body) match {
      case j: JObject =>
        val msg      = j.extract[GtInnerMessage]
        val fromUser = j.obj.find(_._1 == "fromUser").get._2.extract[GtInnerUser]
        Some(MsgFromGitter(id, msg.id, fromUser.id, fromUser.displayName, fromUser.url, msg.text))
      case JNothing   =>
        None
      case _          =>
        val msg = "Received a non-object JSON"
        l.error(msg)
        throw new Exception(msg)
    }
  }

  private def updateSelfId(): Future[Unit] = {
    sttp
      .headers(commonHeaders: _*)
      .get(selfUrl)
      .send()
      .map(_.body)
      .map {
        case Right(body) =>
          val json  = parse(body)
          val first = json.asInstanceOf[JArray].arr.head.asInstanceOf[JObject].obj.toMap
          selfId = Some(first("id").asInstanceOf[JString].s)
          selfId.foreach(id => l.info(s"Set Gitter self id as $id"))
        case Left(str)   =>
          l.error(s"Failed to update Gitter self id, msg: $str")
          timers.startSingleTimer('idUpdate, 'updateId, Duration(1, MINUTES))
      }
      .andThen { case Failure(th) =>
        l.error("Failed to update Gitter self id", th)
      }
  }

  override def receive: Receive = {
    case 'getChats               =>
      getRooms() pipeTo sender()
    case 'updateId               =>
      updateSelfId()
    case MsgRun(r)               =>
      router = r
      // Initialize sink to accept all incoming Gitter messages
      // It is used for all room listeners
      sink = Sink.foreach(router ! _)
      updateSelfId() // Retrieve its own ID for filtering its messages
    case MsgGitterListen(id)     =>
      getMessages(id)
        .map(_.body)
        .map {
          case Right(src) =>
            l.info(s"Started listening to Gitter chat $id")
            src
              .via(Framing.delimiter(streamDelimeter, maximumFrameLength))
              .map(it => parseMessage(id, it.utf8String))
              .collect {
                case Some(msg) if !this.selfId.contains(msg.userId) && msg.userUrl != Configuration.gtUsername => msg
              }
              .runForeach { msg => router ! msg }
              .onComplete {
                case Success(_)  =>
                  l.info(s"Stopped listening to Gitter chat $id, restarting.")
                  self ! MsgGitterListen(id)
                case Failure(th) =>
                  l.error(s"Interrputed listening to Gitter chat $id", th)
                  self ! MsgGitterListen(id)
              }
          case Left(str)  =>
            l.error(s"Failed to initiate connection to Gitter chat $id, msg: $str")
            timers.startSingleTimer(Symbol(id), MsgGitterListen(id), 1 minute)

        }
        .recover { case th =>
          l.error(s"Failed to initiate connection to Gitter chat $id", th)
          timers.startSingleTimer(Symbol(id), MsgGitterListen(id), 1 minute)
        }
    case MsgSendGitter(id, text) =>
      sendMsg(id, text) pipeTo sender()
  }
}
