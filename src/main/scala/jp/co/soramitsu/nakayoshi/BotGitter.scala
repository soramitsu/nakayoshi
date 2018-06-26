package jp.co.soramitsu.nakayoshi

import akka.Done
import akka.actor.{Actor, ActorRef, ActorSystem, Timers}
import akka.stream.Materializer
import akka.stream.scaladsl._
import akka.util.ByteString
import org.json4s, json4s._, native.JsonMethods._
import com.softwaremill.sttp._, akkahttp.AkkaHttpBackend

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}
import scala.util.control.NonFatal
import scala.concurrent.duration._

class BotGitter(token: String)
               (implicit actorSystem: ActorSystem, private val materializer: Materializer)
  extends Actor with Loggable with Timers {

  private var router: Option[ActorRef] = None
  private var sink: Sink[GitterMessage, Future[Done]] = Sink.ignore
  private var selfId: Option[String] = None

  private implicit def dispatcher: ExecutionContextExecutor = context.dispatcher

  private val commonHeaders = Seq(
    "Accept" -> "application/json",
    "Authorization" -> s"Bearer $token"
  )
  private implicit val backend: SttpBackend[Future, Source[ByteString, Any]] =
    AkkaHttpBackend.usingActorSystem(actorSystem)

  private def sendMsg(roomId: String, msg: String): Unit = {
    val url = uri"https://api.gitter.im/v1/rooms/$roomId/chatMessages"
    val headers = commonHeaders :+ ("Content-Type" -> "application/json")
    val bodyJson = JObject("text" -> JString(msg))
    val body = compact(render(bodyJson))
    sttp.headers(headers: _*).body(body).post(url).send()
  }

  private val roomsUrl = uri"https://api.gitter.im/v1/rooms"
  private def getRooms(): Future[List[GitterRoom]] = {
    val response: Future[Response[String]] = sttp.headers(commonHeaders: _*).get(roomsUrl).send()
    response.map(_.body).collect {
      case Right(body) =>
        val json = parse(string2JsonInput(body))
        json.asInstanceOf[JArray].arr.flatMap {
          case it: JObject =>
            val obj: Map[String, json4s.JValue] = it.obj.toMap
            if(obj.contains("id") && obj.contains("name") && obj.contains("uri") &&
              obj.contains("groupId") && obj("groupId").isInstanceOf[JString]) {
              Some(GitterRoom(
                obj("id").asInstanceOf[JString].s,
                obj("name").asInstanceOf[JString].s,
                obj("uri").asInstanceOf[JString].s,
                obj("groupId").asInstanceOf[JString].s))
            } else None
          case _ => None
        }
    }
  }

  private def getMessages(room: String): Future[Response[Source[ByteString, Any]]] = {
    val url = uri"https://stream.gitter.im/v1/rooms/$room/chatMessages"
    sttp.headers(commonHeaders: _*)
      .response(asStream[Source[ByteString, Any]])
      .get(url).send()
  }

  private def parseMessage(id: String, body: String): Option[GitterMessage] = {
    parse(string2JsonInput(body)) match {
      case j: JObject =>
        val json = j.obj.toMap
        val user = json("fromUser").asInstanceOf[JObject].obj.toMap
        Some(GitterMessage(
          id,
          user("id").asInstanceOf[JString].s,
          user("displayName").asInstanceOf[JString].s,
          user("url").asInstanceOf[JString].s,
          json("text").asInstanceOf[JString].s))
      case JNothing =>
        None
      case _ =>
        val msg = "Received a non-object JSON"
        l.error(msg)
        throw new Exception(msg)
    }
  }

  private val selfUrl = uri"https://api.gitter.im/v1/user"
  private def updateSelfId(): Unit = {
    val response: Future[Response[String]] = sttp.headers(commonHeaders: _*).get(selfUrl).send()
    response.map(_.body).map {
      case Right(body) =>
        val json = parse(string2JsonInput(body))
        val first = json.asInstanceOf[JArray].arr.head.asInstanceOf[JObject].obj.toMap
        selfId = Some(first("id").asInstanceOf[JString].s)
        selfId.foreach(id => l.info(s"Set Gitter self id as $id"))
      case Left(str) =>
        l.error(s"Failed to update Gitter self id, msg: $str")
        timers.startSingleTimer('idUpdate, 'updateId, 1 minute)
    }.recover {
      case NonFatal(e) =>
        l.error("Failed to update Gitter self id", e)
    }
  }

  override def receive: Receive = {
    case 'getChats =>
      sender ! getRooms()
    case 'updateId =>
      updateSelfId()
    case MsgRun(r) =>
      router = Some(r)
      // Initialize sink to accept all incoming Gitter messages
      // It is used for all room listeners
      sink = Sink.foreach(r ! _)
      updateSelfId() // Retrieve its own ID for filtering its messages
    case MsgGitterListen(id) =>
      getMessages(id).map(_.body).map {
        case Right(src) =>
          l.info(s"Started listening to Gitter chat $id")
          src.via(Framing.delimiter(ByteString(10), maximumFrameLength = 8192))
            .map(it => parseMessage(id, it.utf8String))
            .collect { case Some(msg) if !this.selfId.contains(msg.userId) && msg.userUrl != Configuration.gtUsername => msg }
            .runForeach { msg => router.foreach(_ ! msg) }
            .onComplete {
              case Success(_) =>
                l.info(s"Stopped listening to Gitter chat $id, restarting.")
                self ! MsgGitterListen(id)
              case Failure(th) =>
                l.error(s"Interrputed listening to Gitter chat $id", th)
                self ! MsgGitterListen(id)
            }
        case Left(str) =>
          l.error(s"Failed to initiate connection to Gitter chat $id, msg: $str")
          timers.startSingleTimer(Symbol(id), MsgGitterListen(id), 1 minute)

      }.recover {
        case NonFatal(e) => l.error(s"Failed to initiate connection to Gitter chat $id", e)
      }
    case MsgSendGitter(id, text) =>
      sendMsg(id, text)
  }
}
