package jp.co.soramitsu.nakayoshi

import Types._
import slick.jdbc.SQLiteProfile.api._

import scala.concurrent.{Future, ExecutionContext}
import scala.util.Random

object Storage extends Loggable {
  private val url = s"jdbc:sqlite:data/db.sqlite"
  private val db = Database.forURL(url, driver = "org.sqlite.JDBC")
  private val files = TableQuery[TableFiles]
  private val conns = TableQuery[TableConnections]
  private val rand = new Random()

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
  class TableConnections(tag: Tag) extends Table[(Int, Option[IdTg], Option[IdGt], Option[IdRc])](tag, "conns") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def telegramId = column[Option[IdTg]]("telegramId", O.Unique)
    def gitterId = column[Option[IdGt]]("gitterId", O.Unique)
    def rocketchatId = column[Option[IdRc]]("rocketchatId", O.Unique)

    override def * = (id, telegramId, gitterId, rocketchatId)
  }

  private def generateAddr(): String =
    rand.alphanumeric.take(32).mkString

  def create(): Unit = {
    l.info(s"DB at: $url")
    db.run(files.schema.create)
    db.run(conns.schema.create)
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
    db.run((files returning files.map(_.id)) += (0, token, addr)).map(_ + addr)
  }

  // Return an address if it already exists
  def getFileAddress(token: String)(implicit ec: ExecutionContext): Future[Option[String]] =
    db.run(files.filter(_.token === token).map(i => (i.id, i.addr)).result.headOption)
      .map(_.map { case (id, addr) => id + addr })
}
