package me.vilunov.nakayoshi

import java.util.{Observable, Observer}

import com.keysolutions.ddpclient._
import com.softwaremill.sttp._, akkahttp.AkkaHttpBackend
import akka.actor.{Actor, ActorRef, ActorSystem}
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.json4s._, native.JsonMethods.{compact, parse, render}

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.control.NonFatal
import scala.collection.JavaConverters._

class BotRocketchat(private val pathRaw: String,
                    private val user: String,
                    private val password: String)
                   (implicit actorSystem: ActorSystem, private val materializer: Materializer)
  extends Actor with Loggable {

  private val client = new DDPClient(pathRaw, 443, true)
  private var observer: RCObserver = _
  private var listenQueue: Seq[String] = Seq()

  private val path = s"https://$pathRaw"
  private implicit val backend: SttpBackend[Future, Source[ByteString, Any]] =
    AkkaHttpBackend.usingActorSystem(actorSystem)
  private implicit def dispatcher: ExecutionContextExecutor = context.dispatcher

  private var router: ActorRef = _

  private var loginCall: Int = _
  private var loggedIn: Boolean = false
  private var tokenId: Option[(String, String)] = None

  private def authHeaders: Seq[(String, String)] = tokenId.toSeq.flatMap { it => Seq(
    "X-Auth-Token" -> it._1,
    "X-User-Id" -> it._2
  ) }
  private val commonHeaders: Seq[(String, String)] = Seq(
    "Accept" -> "application/json",
    "Content-Type" -> "application/json"
  )

  private val loginUrl = uri"$path/api/v1/login"

  /**
    * Tries to login into the Rocketchat
    * @return future with token and user ID
    */
  private def login(): Future[(String, String)] = {
    val bodyJson = JObject("username" -> JString(user), "password" -> JString(password))
    sttp.headers(commonHeaders: _*).body(compact(render(bodyJson))).post(loginUrl).send().map(_.body).collect {
      case Right(body) =>
        val data = parse(string2JsonInput(body)).asInstanceOf[JObject].values("data").asInstanceOf[Map[String, Any]]
        l.info("Logged into a Rocketchat account")
        (data("authToken").asInstanceOf[String], data("userId").asInstanceOf[String])
    }.recover {
      case NonFatal(e) =>
        l.error("Failed to log into a Rocketchat account", e)
        throw e
    }
  }

  /**
    * Gets the ID of the chat group
    * @param name chat group name (e.g. "general")
    * @return future with the group's ID
    */
  private def getChanId(name: String): Future[String] = {
    val url = uri"$path/api/v1/channels.info?roomName=$name"
    sttp.headers(commonHeaders ++ authHeaders: _*).get(url).send().map(_.body).collect {
      case Right(body) =>
        parse(string2JsonInput(body)).asInstanceOf[JObject]
          .values("channel").asInstanceOf[Map[String, Any]]("_id").asInstanceOf[String]
    }.recover {
      case NonFatal(e) =>
        l.error("Failed to login into Rocketchat account", e)
        throw e
    }
  }

  private val joinUrl = uri"$path/api/v1/channels.join"

  /**
    * Joins the group
    * @param id group's ID
    * @return future with unit when complete
    */
  private def joinChat(id: String): Future[Unit] = {
    val bodyJson = JObject("roomId" -> JString(id))
    sttp.headers(commonHeaders ++ authHeaders: _*).body(compact(render(bodyJson))).post(joinUrl).send().map(_.body).collect {
      case Right(_) =>
        l.info(s"Joined Rocketchat room #$id")
        ()
    }.recover {
      case NonFatal(e) =>
        l.error("Failed to join a Rocketchat room", e)
        throw e
    }
  }

  private val joinedUrl = uri"$path/api/v1/channels.list.joined"

  /**
    * Get map of joined chats
    * @return map of ID -> name
    */
  private def getChats(): Future[Map[String, String]] = {
    sttp.headers(commonHeaders ++ authHeaders: _*).get(joinedUrl).send().map(_.body).collect {
      case Right(body) =>
        parse(string2JsonInput(body)).asInstanceOf[JObject]
          .values("channels").asInstanceOf[List[Any]].map { it =>
          val i = it.asInstanceOf[Map[String, Any]]
          (i("_id").asInstanceOf[String], i("name").asInstanceOf[String])
        }.toMap
    }.recover {
      case NonFatal(e) =>
        l.error("Failed to send into a Rocketchat group", e)
        throw e
    }
  }

  private val messageUrl = uri"$path/api/v1/chat.postMessage"
  private def sendMessage(id: String, msg: String): Future[Unit] = {
    val bodyJson = JObject("roomId" -> JString(id), "text" -> JString(msg))
    sttp.headers(commonHeaders ++ authHeaders: _*).body(compact(render(bodyJson))).post(messageUrl).send().map(_.body).collect {
      case Right(_) => ()
    }.recover {
      case NonFatal(e) =>
        l.error("Failed to send into a Rocketchat group", e)
        throw e
    }
  }

  private def subscribe(id: String): Unit = {
    val tmp = client.subscribe("stream-room-messages", Array[AnyRef](id, Boolean.box(false)))
    l.info(s"Added listener for Rocketchat room #$id")
  }

  override def receive: Receive = {
    case 'getChats =>
      sender ! getChats()
    case ServerResponse(args) =>
      if(!args.get("msg").contains("ping")) l.debug("Rocketchat response: " + args)
      if(args.get("id").contains(loginCall.toString)) {
        loggedIn = true
        listenQueue.foreach(subscribe)
        listenQueue = Seq()
      } else if(args.get("msg").contains("changed") && args.get("collection").contains("stream-room-messages")) {
        args("fields").asInstanceOf[Map[String, Any]]("args").asInstanceOf[Seq[Any]].foreach { case info: Map[String, Any] =>
          val chat = info("rid").asInstanceOf[String]
          val username = info("u").asInstanceOf[Map[String, Any]]("username").asInstanceOf[String]
          val content = info("msg").asInstanceOf[String]
          val t = info.get("t").asInstanceOf[Option[String]]
          if(!t.contains("uj") && username != user) router ! RocketchatMessage(chat, username, content)
        }
      }
    case MsgSendGitter(id, text) =>
      sendMessage(id, text)
    case MsgRun(r) =>
      observer = new RCObserver(self)
      router = r
      client.connect()
      client.addObserver(observer)
      login().foreach { case (token, id) =>
          loginCall = client.call("login", Array(new TokenAuth(token)))
          tokenId = Some((token, id))
      }
    case MsgRcJoin(name) =>
      sender ! getChanId(name).map(joinChat)
    case MsgRcListen(id) =>
      if(loggedIn) subscribe(id)
      else listenQueue = listenQueue :+ id

  }
}

class RCObserver(val bot: ActorRef) extends Observer {
  override def update(observable: Observable, o: Any): Unit = {
    def convert(m: Any): Any = m match {
      case o: java.util.Map[String, AnyRef] => o.asScala.toMap.mapValues(convert)
      case l: java.util.List[AnyRef] => l.asScala.map(convert)
      case j => j
    }
    o match {
      case m: java.util.Map[String, AnyRef] => bot ! ServerResponse(m.asScala.toMap.mapValues(convert))
      case _ =>
    }
  }

}

case class ServerResponse(vals: Map[String, Any])
