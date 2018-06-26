package jp.co.soramitsu.nakayoshi

import akka.actor.{Actor, ActorRef}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import scala.collection.mutable
import scala.concurrent.{ExecutionContextExecutor, Future}

class ActorMsgRouter(val tg: ActorRef,
                     val gitter: ActorRef,
                     val rocketchat: ActorRef,
                     val hostname: String)
  extends Actor with Loggable {

  tg ! MsgRun(self)
  gitter ! MsgRun(self)
  rocketchat ! MsgRun(self)

  private implicit def dispatcher: ExecutionContextExecutor = context.dispatcher

  private val telegramChats = mutable.HashMap[Long, TelegramChat]()
  private var gitterChats = List[GitterRoom]()
  private var gitterChatsLastUpdate: Long = 0
  private var rcChats = Map[String, String]()
  private var rcChatsLastUpdate: Long = 0

  private var conns = Seq[Connection]()
  private val connsTg = mutable.HashMap[Long, Connection]()
  private val connsGt = mutable.HashMap[String, Connection]()
  private val connsRc = mutable.HashMap[String, Connection]()

  private val chatlistCacheTTL = 15 seconds

  implicit val timeout: Timeout = Timeout(2 seconds)

  private def addConnection(conn: Connection): Unit = {
    conn.gtId.foreach { id =>
      gitter ! MsgGitterListen(id)
      connsGt.put(id, conn)
    }
    conn.tgId.foreach { id =>
      connsTg.put(id, conn)
    }
    conn.rcId.foreach { id =>
      rocketchat ! MsgRcListen(id)
      connsRc.put(id, conn)
    }
    conns = conns :+ conn
  }

  override def receive: Receive = {
    case 'getGtChats =>
      if(System.currentTimeMillis() - gitterChatsLastUpdate < chatlistCacheTTL.toMillis)
        sender ! gitterChats
      else {
        val chats = (gitter ? 'getChats).flatMap(_.asInstanceOf[Future[List[GitterRoom]]])
        chats.foreach { chats =>
          gitterChatsLastUpdate = System.currentTimeMillis()
          gitterChats = chats
        }
        sender ! chats
      }
    case 'getRcChats =>
      if(System.currentTimeMillis() - rcChatsLastUpdate < chatlistCacheTTL.toMillis)
        sender ! rcChats
      else {
        val chats = (rocketchat ? 'getChats).flatMap(_.asInstanceOf[Future[Map[String, String]]])
        chats.foreach { chats =>
          rcChatsLastUpdate = System.currentTimeMillis()
          rcChats = chats
        }
        sender ! chats
      }
    case 'getTgChats =>
      sender ! telegramChats.toSeq
    case 'getConns =>
      sender ! conns
    case 'updateConnsFromDB =>
      Storage.getConns().foreach { ret: Seq[(Int, Connection)] =>
        ret.foreach { case (_, conn) => addConnection(conn) }
      }
    case m: MsgRcJoin =>
      sender ! (rocketchat ? m).flatMap(_.asInstanceOf[Future[Unit]])
    case MsgAddTgChat(chatId, chat) =>
      telegramChats.put(chatId, chat)
    case MsgConnect(connection) =>
      addConnection(connection)
      Storage.insertConn(connection)
    case m @ MsgFromTelegram(chatId, user, alias, msg, file, fwd) =>
      l.info("Telegram message received: " + m.toString)
      val userfill = alias.fold(user)(alias => s"[$user](https://t.me/$alias)")
      if(msg.isDefined || file.isDefined) connsTg.get(chatId).foreach { conn =>
        conn.gtId.foreach { id =>
          val text = s"**$userfill** *" + fwd.fold("")(_ + " ") + "via telegram*\n" +
            file.fold("")(url => s"![Photo]($hostname$url)\n") +
            msg.fold("") { case (str, ents) => MarkdownConverter.tg2gt(str, ents) }
          gitter ! MsgSendGitter(id, text)
        }
        conn.rcId.foreach { id =>
          val text = s"*$userfill* _" + fwd.fold("")(_ + " ") + "via telegram_\n" +
            file.fold("")(url => s"![Photo]($hostname$url)\n") +
            msg.fold("") { case (str, ents) => MarkdownConverter.tg2rc(str, ents) }
          rocketchat ! MsgSendGitter(id, text)
        }
      }
    case m @ GitterMessage(chatId, _, username, userUrl, text) =>
      l.info("Gitter message received: " + m.toString)
      connsGt.get(chatId).foreach { conn =>
        conn.tgId.foreach { id =>
          tg ! MsgSendTelegram(id,
            s"[$username](https://gitter.im$userUrl) _via gitter_\n" +
              MarkdownConverter.gt2tg(text)
          )
        }
        conn.rcId.foreach { id =>
          val msg = s"*[$username](https://gitter.im$userUrl)* _via gitter_\n" +
            MarkdownConverter.gt2rc(text)
          rocketchat ! MsgSendGitter(id, msg)
        }
      }
    case m @ RocketchatMessage(chatId, username, text) =>
        l.info("Rocketchat message received: " + m.toString)
      connsRc.get(chatId).foreach { conn =>
        conn.tgId.foreach { id =>
          tg ! MsgSendTelegram(id,
            s"[$username](https://${Configuration.rcPath}/direct/$username) _via rocketchat_\n" +
              MarkdownConverter.rc2tg(text)
          )
        }
        conn.gtId.foreach { id =>
          val msg = s"**[$username](https://${Configuration.rcPath}/direct/$username)** *via rocketchat*\n" +
            MarkdownConverter.rc2gt(text)
          gitter ! MsgSendGitter(id, msg)
        }
      }
  }
}
