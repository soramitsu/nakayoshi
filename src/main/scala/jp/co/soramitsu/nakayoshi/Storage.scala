package jp.co.soramitsu.nakayoshi

import jp.co.soramitsu.nakayoshi.Types._
import jp.co.soramitsu.nakayoshi.internals.Loggable
import slick.dbio.Effect
import slick.jdbc.SQLiteProfile.api._
import slick.sql.FixedSqlAction

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

case class ConnectedMessage(telegramChat: Option[ChatTg], telegramMsg: Option[MsgTg],
                            gitterChat: Option[ChatGt], gitterMsg: Option[MsgGt],
                            rocketchatChat: Option[ChatRc], rocketchatMessage: Option[MsgRc]) {

  require(telegramChat.isDefined == telegramMsg.isDefined)
  require(gitterChat.isDefined == gitterMsg.isDefined)
  require(rocketchatChat.isDefined == rocketchatMessage.isDefined)
  @inline private def combine[A, B](a: Option[A], b: Option[B]): Option[(A, B)] = for (c <- a; d <- b) yield (c, d)
  def telegram: Option[(ChatTg, MsgTg)] = combine(telegramChat, telegramMsg)
  def gitter: Option[(ChatGt, MsgGt)] = combine(gitterChat, gitterMsg)
  def rocketchat: Option[(ChatRc, MsgRc)] = combine(rocketchatChat, rocketchatMessage)
}

object ConnectedMessage {
  def create(telegram: Option[(ChatTg, MsgTg)] = None,
             gitter: Option[(ChatGt, MsgGt)] = None,
             rocketchat: Option[(ChatRc, MsgRc)] = None): ConnectedMessage =
    apply(telegram.map(_._1), telegram.map(_._2), gitter.map(_._1), gitter.map(_._2), rocketchat.map(_._1), rocketchat.map(_._2))
}

object Storage extends Loggable {
  private val url = s"jdbc:sqlite:data/db.sqlite"
  private val db = Database.forURL(url, driver = "org.sqlite.JDBC")
  private val rand = new Random()
  val files = TableQuery[TableFiles]
  val conns = TableQuery[TableConnections]
  val messages = TableQuery[TableMessages]

  // Information about files, including:
  // - Token for identifying duplicates
  // - Address name for accessing externally
  class TableFiles(tag: Tag) extends Table[(Int, String, String)](tag, "files") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def addr = column[String]("addr")
    def token = column[String]("token", O.Unique)

    override def * = (id, addr, token)
  }

  // Information about bridge connection, holds chat IDs
  class TableConnections(tag: Tag) extends Table[(Int, Option[ChatTg], Option[ChatGt], Option[ChatRc])](tag, "conns") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def telegramId = column[Option[ChatTg]]("telegram_id", O.Unique)
    def gitterId = column[Option[ChatGt]]("gitter_id", O.Unique)
    def rocketchatId = column[Option[ChatRc]]("rocketchat_id", O.Unique)

    override def * = (id, telegramId, gitterId, rocketchatId)
  }

  class TableMessages(tag: Tag) extends Table[ConnectedMessage](tag, "messages") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def telegramChat = column[Option[ChatTg]]("telegram_chat")
    def telegramMsg = column[Option[MsgTg]]("telegram_msg")
    def gitterChat = column[Option[ChatGt]]("gitter_chat")
    def gitterMsg = column[Option[MsgGt]]("gitter_msg")
    def rocketchatChat = column[Option[ChatRc]]("rocketchat_chat")
    def rocketchatMsg = column[Option[MsgRc]]("rocketchat_msg")

    def telegramIndex = index("telegram", (telegramChat, telegramMsg), unique = true)
    def gitterIndex = index("gitter", (gitterChat, gitterMsg), unique = true)
    def rocketchatIndex = index("rocketchat", (rocketchatChat, rocketchatMsg), unique = true)

    override def * =
      (telegramChat, telegramMsg, gitterChat, gitterMsg, rocketchatChat, rocketchatMsg) <>
        ((ConnectedMessage.apply _).tupled, ConnectedMessage.unapply)
  }

  def exec[T](query: DBIOAction[T, NoStream, Effect.Read with Effect.Write]): Future[T] = db.run(query)

  private def generateAddr(): String =
    rand.alphanumeric.take(32).mkString

  def create(): Future[Unit] = {
    l.info(s"DB at: $url")
    db.run((files.schema ++ conns.schema ++ messages.schema).create)
  }

  def insertConn(conn: Connection)(implicit ec: ExecutionContext): Future[Unit] =
    db.run(conns += (0, conn.tgId, conn.gtId, conn.rcId)).map(_ => ())

  // Get list of all connections
  def getConns()(implicit ec: ExecutionContext): Future[Seq[(Int, Connection)]] =
    db.run(conns.map(i => (i.telegramId, i.gitterId, i.rocketchatId, i.id)).result).map { seq =>
      seq.map(it => (it._4, Connection(it._1, it._2, it._3)))
    }

  // Add to database and return public address
  def insertFile(token: String, postfix: String)(implicit ec: ExecutionContext): Future[String] = {
    val addr = generateAddr() + postfix
    val i: FixedSqlAction[Int, NoStream, Effect.Write] = (files returning files.map(_.id)) += (0, token, addr)
    db.run((files returning files.map(_.id)) += (0, token, addr)).map(_ + addr)
  }

  // Return an address if it already exists
  def getFileAddress(token: String)(implicit ec: ExecutionContext): Future[Option[String]] =
    db.run(files.filter(_.token === token).map(i => (i.id, i.addr)).result.headOption)
      .map(_.map { case (id, addr) => id + addr })

  def insertMessage(msg: ConnectedMessage): Future[Int] =
    db.run((messages returning messages.map(_.id)) += msg)

  def setMessageTelegram(id: Int, chat: ChatTg, msg: MsgTg): Future[Any] =
    db.run(messages.filter(_.id === id).map(i => (i.telegramChat, i.telegramMsg)).update(Some(chat), Some(msg)))

  def setMessageGitter(id: Int, chat: ChatGt, msg: MsgGt): Future[Any] =
    db.run(messages.filter(_.id === id).map(i => (i.gitterChat, i.gitterMsg)).update(Some(chat), Some(msg)))

  def setMessageRocketchat(id: Int, chat: ChatRc, msg: MsgRc): Future[Any] =
    db.run(messages.filter(_.id === id).map(i => (i.rocketchatChat, i.rocketchatMsg)).update(Some(chat), Some(msg)))

}
