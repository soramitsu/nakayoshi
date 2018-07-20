package jp.co.soramitsu.nakayoshi

import Types._
import slick.jdbc.SQLiteProfile.api._

import scala.concurrent.{Future, ExecutionContext}
import scala.util.Random

object Storage extends Loggable {
  private val url = s"jdbc:sqlite:data/db.sqlite"
  private val db = Database.forURL(url, driver="org.sqlite.JDBC")
  private val photos = TableQuery[DbPhoto]
  private val conns = TableQuery[DbConn]
  private val rand = new Random()

  // Information about photos, including:
  // - Token for identifying duplicates
  // - Address name for accessing externally
  class DbPhoto(tag: Tag) extends Table[(String, String)](tag, "photos") {
    def addr = column[String]("addr", O.PrimaryKey)
    def token = column[String]("token", O.Unique)

    override def * = (addr, token)
  }

  // Information about transport connection, holds chat IDs
  class DbConn(tag: Tag) extends Table[(Int, Option[IdTg], Option[IdGt], Option[IdRc])](tag, "conns") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def telegramId = column[Option[IdTg]]("telegramId", O.Unique)
    def gitterId = column[Option[IdGt]]("gitterId", O.Unique)
    def rocketchatId = column[Option[IdRc]]("rocketchatId", O.Unique)

    override def * = (id, telegramId, gitterId, rocketchatId)
  }

  private def generateAddr(): String =
    rand.alphanumeric.take(32).mkString

  def create(): Future[Unit] = {
    l.info(s"DB at: $url")
    db.run(photos.schema.create)
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
    db.run(photos += (token, addr)).map(_ => addr)
  }

  // Return an address if it already exists
  def getAddrByToken(token: String)(implicit ec: ExecutionContext): Future[Option[String]] =
    db.run(photos.filter(_.token === token).map(_.addr).result.headOption)
}
