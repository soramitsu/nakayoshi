package jp.co.soramitsu.nakayoshi

import jp.co.soramitsu.nakayoshi.Types._
import jp.co.soramitsu.nakayoshi.Storage.insertMessage
import jp.co.soramitsu.nakayoshi.internals.Loggable

import akka.actor.{Actor, ActorRef}
import akka.pattern.{ask, pipe}
import akka.util.Timeout

import scala.concurrent.duration._
import scala.collection.mutable
import scala.concurrent.ExecutionContext

class ActorMsgRouter(val tg: ActorRef,
                     val gitter: ActorRef,
                     val rocketchat: ActorRef,
                     val hostname: String)
  extends Actor with Loggable {

  tg ! MsgRun(self)
  gitter ! MsgRun(self)
  rocketchat ! MsgRun(self)

  private lazy implicit val dispatcher: ExecutionContext = context.dispatcher
  private implicit val timeout: Timeout = Timeout(2, SECONDS)
  private implicit val hs: HandlerSettings = HandlerSettings(hostname)

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

  private def addConnection(conn: Connection): Unit = {
    conn.gtId.foreach { id =>
      gitter ! MsgGitterListen(id)
    }
    conn.rcId.foreach { id =>
      rocketchat ! MsgRcListen(id)
    }
    conns = conns :+ conn
  }

  override def receive: Receive = {
    case 'getGtChats =>
      if(System.currentTimeMillis() - gitterChatsLastUpdate < chatlistCacheTTL.toMillis)
        sender() ! gitterChats
      else {
        val chats = (gitter ? 'getChats).mapTo[List[GitterRoom]]
        chats.foreach { chats =>
          gitterChatsLastUpdate = System.currentTimeMillis()
          gitterChats = chats
        }
        chats pipeTo sender()
      }
    case 'getRcChats =>
      if(System.currentTimeMillis() - rcChatsLastUpdate < chatlistCacheTTL.toMillis)
        sender() ! rcChats
      else {
        val chats = (rocketchat ? 'getChats).mapTo[Map[String, String]]
        chats.foreach { chats =>
          rcChatsLastUpdate = System.currentTimeMillis()
          rcChats = chats
        }
        chats pipeTo sender()
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
      (rocketchat ? m) pipeTo sender()
    case MsgAddTgChat(chatId, chat) =>
      telegramChats.put(chatId, chat)
    case MsgConnect(connection) =>
      addConnection(connection)
      Storage.insertConn(connection)
    case m: MsgFrom =>
      l.info("Message received: " + m.toString)
      conns.find(m.triggersConnection).foreach { conn =>
        val messageId = insertMessage(m.emptyMessage)
        for (tgChat <- conn.tgId; msg <- m.toTelegram(tgChat)) {
          val msgId = (tg ? msg).mapTo[MsgTg]
          for (dbId <- messageId; tgId <- msgId)
            Storage.setMessageTelegram(dbId, tgChat, tgId)
        }
        for (gtChat <- conn.gtId; msg <- m.toGitter(gtChat)) {
          val msgId = (gitter ? msg).mapTo[MsgGt]
          for (dbId <- messageId; gtId <- msgId)
            Storage.setMessageGitter(dbId, gtChat, gtId)
        }
        for (rcChat <- conn.rcId; msg <- m.toRocketchat(rcChat)) {
          val msgId = (rocketchat ? msg).mapTo[MsgRc]
          for (dbId <- messageId; rcId <- msgId)
            Storage.setMessageRocketchat(dbId, rcChat, rcId)
        }
      }
  }
}
