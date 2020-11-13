package jp.co.soramitsu.nakayoshi

import java.util.{Observable, Observer}

import jp.co.soramitsu.nakayoshi.Types._
import jp.co.soramitsu.nakayoshi.internals.Loggable

import com.keysolutions.ddpclient._
import com.softwaremill.sttp._, akkahttp.AkkaHttpBackend
import akka.actor.{Actor, ActorRef, ActorSystem, Timers}
import akka.pattern.pipe
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.json4s._
import org.json4s.native.JsonMethods.{render, compact}
import org.json4s.native.JsonParser.parse
import org.json4s.native.Serialization.read

import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConverters._
import scala.concurrent.duration._

case class RcInnerMessage(_id: String, msg: String, u: RcInnerUser)
case class RcInnerUser(_id: String, name: String, username: String)
case class RcInnerPostActionResult(channel: String, message: RcInnerMessage)

class BotRocketchat(private val pathRaw: String, private val user: String, private val password: String)(implicit
    actorSystem: ActorSystem
) extends Actor with Loggable with Timers {

  private val client                   = new DDPClient(pathRaw, 443, true)
  private var observer: RCObserver     = _
  private var listenQueue: Seq[String] = Seq()

  private implicit val backend: SttpBackend[Future, Source[ByteString, Any]] =
    AkkaHttpBackend.usingActorSystem(actorSystem)
  private lazy implicit val executionContext: ExecutionContext               = context.dispatcher
  private implicit val formats: Formats                                      = DefaultFormats

  private var router: ActorRef = _

  private var loginCall: Int                    = _
  private var loggedIn: Boolean                 = false
  private var tokenId: Option[(String, String)] = None

  private def authHeaders: Seq[(String, String)]   = tokenId.toSeq.flatMap { it =>
    Seq(
      "X-Auth-Token" -> it._1,
      "X-User-Id"    -> it._2
    )
  }
  private val commonHeaders: Seq[(String, String)] = Seq(
    "Accept"       -> "application/json",
    "Content-Type" -> "application/json"
  )

  private val path                      = s"https://$pathRaw"
  private val loginUrl                  = uri"$path/api/v1/login"
  private val joinUrl                   = uri"$path/api/v1/channels.join"
  private val joinedUrl                 = uri"$path/api/v1/channels.list.joined"
  private val messageUrl                = uri"$path/api/v1/chat.postMessage"
  private def chanInfoUrl(name: String) = uri"$path/api/v1/channels.info?roomName=$name"

  /** Tries to login into the Rocketchat
    * @return future with token and user ID
    */
  private def login(): Future[(String, String)] = {
    val bodyJson = JObject("username" -> JString(user), "password" -> JString(password))
    sttp
      .headers(commonHeaders: _*)
      .body(compact(render(bodyJson)))
      .post(loginUrl)
      .send()
      .map(_.body)
      .collect { case Right(body) =>
        val data = parse(body)
          .asInstanceOf[JObject]
          .values("data")
          .asInstanceOf[Map[String, Any]]
        l.info("Logged into a Rocketchat account")
        (data("authToken").asInstanceOf[String], data("userId").asInstanceOf[String])
      }
      .recover { case th =>
        l.error("Failed to log into a Rocketchat account", th)
        throw th
      }
  }

  /** Gets the ID of the chat group
    * @param name chat group name (e.g. "general")
    * @return future with the group's ID
    */
  private def getChanId(name: String): Future[String] = {
    sttp
      .headers(commonHeaders ++ authHeaders: _*)
      .get(chanInfoUrl(name))
      .send()
      .map(_.body)
      .collect { case Right(body) =>
        parse(body)
          .asInstanceOf[JObject]
          .values("channel")
          .asInstanceOf[Map[String, Any]]("_id")
          .asInstanceOf[String]
      }
      .recover { case th =>
        l.error("Failed to login into Rocketchat account", th)
        throw th
      }
  }

  /** Joins the group
    * @param id group's ID
    * @return future with unit when complete
    */
  private def joinChat(id: String): Future[Unit] = {
    val bodyJson = JObject("roomId" -> JString(id))
    sttp
      .headers(commonHeaders ++ authHeaders: _*)
      .body(compact(render(bodyJson)))
      .post(joinUrl)
      .send()
      .map(_.body)
      .collect { case Right(_) =>
        l.info(s"Joined Rocketchat room #$id")
        ()
      }
      .recover { case th =>
        l.error("Failed to join a Rocketchat room", th)
        throw th
      }
  }

  /** Get map of joined chats
    * @return map of ID -> name
    */
  private def getChats(): Future[Map[String, String]] = {
    sttp
      .headers(commonHeaders ++ authHeaders: _*)
      .get(joinedUrl)
      .send()
      .map(_.body)
      .collect { case Right(body) =>
        parse(body)
          .asInstanceOf[JObject]
          .values("channels")
          .asInstanceOf[List[Any]]
          .map { it =>
            val i = it.asInstanceOf[Map[String, Any]]
            (i("_id").asInstanceOf[String], i("name").asInstanceOf[String])
          }
          .toMap
      }
      .recover { case th =>
        l.error("Failed to send into a Rocketchat group", th)
        throw th
      }
  }

  private def sendMessage(id: String, msg: String): Future[MsgRc] = {
    val bodyJson = JObject("roomId" -> JString(id), "text" -> JString(msg))
    sttp
      .headers(commonHeaders ++ authHeaders: _*)
      .body(compact(render(bodyJson)))
      .post(messageUrl)
      .send()
      .map(_.body)
      .collect { case Right(body) => read[RcInnerPostActionResult](body).message._id }
      .recover { case th =>
        l.error("Failed to send into a Rocketchat group", th)
        throw th
      }
  }

  private def subscribe(id: String): Unit = {
    val tmp = client.subscribe("stream-room-messages", Array[AnyRef](id, Boolean.box(false)))
    l.info(s"Added listener for Rocketchat room #$id")
  }

  override def receive: Receive = {
    case 'getChats               =>
      getChats() pipeTo sender()
    case ServerResponse(args)    =>
      if (!args.get("msg").contains("ping")) l.info("Rocketchat response: " + args)
      if (args.get("id").contains(loginCall.toString)) {
        loggedIn = true
        listenQueue.foreach(subscribe)
      } else if (args.get("msg").contains("changed") && args.get("collection").contains("stream-room-messages")) {
        args("fields")
          .asInstanceOf[Map[String, Any]]("args")
          .asInstanceOf[Seq[Any]]
          .foreach { case info: Map[String, Any] =>
            val chatId   = info("rid").asInstanceOf[String]
            val msgId    = info("_id").asInstanceOf[String]
            val username = info("u").asInstanceOf[Map[String, Any]]("username").asInstanceOf[String]
            val content  = info("msg").asInstanceOf[String]
            val t        = info.get("t").asInstanceOf[Option[String]]
            // the field `t` contains "uj" when the message is a 'user joined' notification,
            // and "ul" when it is a 'user left' notification
            // it is not defined for ordinary messages (most likely / no eto netochno)
            if (t.isEmpty && username != user)
              router ! MsgFromRocketchat(chatId, msgId, username, content)
          }
      } else if (args.get("msg").contains("closed") && !timers.isTimerActive('reconnect_timer)) {
        timers.startSingleTimer('reconnect_timer, 'connect, 1 minute)
      }
    case MsgSendGitter(id, text) =>
      sendMessage(id, text) pipeTo sender()
    case MsgRun(r)               =>
      router = r
      observer = new RCObserver(self)
      self ! 'connect
    case 'connect                =>
      client.connect()
      client.addObserver(observer)
      login().foreach { case (token, id) =>
        loginCall = client.call("login", Array(new TokenAuth(token)))
        tokenId = Some((token, id))
      }
    case MsgRcJoin(name)         =>
      sender ! getChanId(name).map(joinChat)
    case MsgRcListen(id)         =>
      if (loggedIn) subscribe(id)
      else listenQueue = listenQueue :+ id

  }
}

class RCObserver(val bot: ActorRef) extends Observer {
  private def convert(m: Any): Any = m match {
    case o: java.util.Map[String, AnyRef] => o.asScala.toMap.mapValues(convert)
    case l: java.util.List[AnyRef]        => l.asScala.map(convert)
    case j                                => j
  }

  override def update(observable: Observable, o: Any): Unit = o match {
    case m: java.util.Map[String, AnyRef] =>
      bot ! ServerResponse(m.asScala.toMap.mapValues(convert))
    case _                                =>
  }
}

case class ServerResponse(vals: Map[String, Any])
