package jp.co.soramitsu.nakayoshi

import jp.co.soramitsu.nakayoshi.Types._

import akka.actor.ActorRef
import com.bot4s.telegram.models.MessageEntity

// For starting actors
case class MsgRun(router: ActorRef)

// When a message is received, sent to the router
sealed case class HandlerSettings(hostname: String)
sealed trait MsgFrom {
  def emptyMessage: ConnectedMessage
  def triggersConnection(conn: Connection): Boolean
  def toTelegram(id: ChatTg)(implicit hs: HandlerSettings): Option[Any]
  def toGitter(id: ChatGt)(implicit hs: HandlerSettings): Option[Any]
  def toRocketchat(id: ChatRc)(implicit hs: HandlerSettings): Option[Any]
}

case class MsgFromTelegram(
    chatId: ChatTg,
    msgId: MsgTg,
    user: String,
    alias: Option[String],
    msg: Option[(String, Seq[MessageEntity])],
    fileUrl: Option[String],
    fwd: Option[String]
) extends MsgFrom {

  override def emptyMessage: ConnectedMessage =
    ConnectedMessage.create(telegram = Some(chatId, msgId))

  override def triggersConnection(conn: Connection): Boolean = conn.tgId.contains(chatId)

  override def toTelegram(id: ChatTg)(implicit hs: HandlerSettings): Option[Any] = None

  override def toGitter(id: ChatGt)(implicit hs: HandlerSettings): Option[Any] = if (
    msg.isDefined || fileUrl.isDefined
  ) {
    val userfill = alias.fold(user)(alias => s"[$user](https://t.me/$alias)")
    val text     = s"**$userfill** *" + fwd.fold("")(_ + " ") + "via telegram*\n" +
      fileUrl.fold("")(url => s"![Photo](${hs.hostname}$url)\n") +
      msg.fold("") { case (str, ents) => MarkdownConverter.tg2gt(str, ents) }
    Some(MsgSendGitter(id, text))
  } else None

  override def toRocketchat(id: ChatRc)(implicit hs: HandlerSettings): Option[Any] = if (
    msg.isDefined || fileUrl.isDefined
  ) {
    val userfill = alias.fold(user)(alias => s"[$user](https://t.me/$alias)")
    val text     = s"*$userfill* _" + fwd.fold("")(_ + " ") + "via telegram_\n" +
      fileUrl.fold("")(url => s"![Photo](${hs.hostname}$url)\n") +
      msg.fold("") { case (str, ents) => MarkdownConverter.tg2rc(str, ents) }
    Some(MsgSendGitter(id, text))
  } else None
}

case class MsgFromGitter(
    chatId: ChatGt,
    msgId: MsgGt,
    userId: String,
    username: String,
    userUrl: String,
    content: String
) extends MsgFrom {

  override def emptyMessage: ConnectedMessage =
    ConnectedMessage.create(gitter = Some(chatId, msgId))

  override def triggersConnection(conn: Connection): Boolean = conn.gtId.contains(chatId)

  override def toTelegram(id: ChatTg)(implicit hs: HandlerSettings): Option[Any] = {
    val message  =
      s"[$username](https://gitter.im$userUrl) _via gitter_\n" +
        MarkdownConverter.gt2tg(content)
    val fallback = s"$username via gitter\n" + content
    Some(MsgSendTelegram(id, message, fallback))
  }

  override def toGitter(id: ChatGt)(implicit hs: HandlerSettings): Option[Any] = None

  override def toRocketchat(id: ChatRc)(implicit hs: HandlerSettings): Option[Any] = Some(
    MsgSendGitter(
      id,
      s"*[$username](https://gitter.im$userUrl)* _via gitter_\n" +
        MarkdownConverter.gt2rc(content)
    )
  )
}

case class MsgFromRocketchat(chatId: ChatRc, msgId: MsgRc, username: String, content: String) extends MsgFrom {
  override def emptyMessage: ConnectedMessage =
    ConnectedMessage.create(rocketchat = Some(chatId, msgId))

  override def triggersConnection(conn: Connection): Boolean = conn.rcId.contains(chatId)

  override def toTelegram(id: ChatTg)(implicit hs: HandlerSettings): Option[Any] = {
    val message  =
      s"[$username](https://${Configuration.rcPath}/direct/$username) _via rocketchat_\n" +
        MarkdownConverter.gt2tg(content)
    val fallback = s"$username via gitter\n" + content
    Some(MsgSendTelegram(id, message, fallback))
  }

  override def toGitter(id: ChatGt)(implicit hs: HandlerSettings): Option[Any] = Some(
    MsgSendGitter(
      id,
      s"**[$username](https://${Configuration.rcPath}/direct/$username)** *via rocketchat*\n" +
        MarkdownConverter.rc2gt(content)
    )
  )

  override def toRocketchat(id: ChatRc)(implicit hs: HandlerSettings): Option[Any] = None
}

case class MsgAddTgChat(chatId: ChatTg, chat: TelegramChat) // Adding a Telegram chat to list
case class MsgConnect(connection: Connection)               // Adding a connection to router

case class MsgGitterListen(id: ChatGt) // Initiate a listening connection to a Gitter room

// For sending messages
case class MsgSendGitter(id: ChatGt, text: String) // Not only Gitter but Rocketchat also :)
case class MsgSendTelegram(id: ChatTg, text: String, fallback: String)

case class MsgRcJoin(name: ChatRc)
case class MsgRcListen(id: ChatRc)

case class MsgTgListen(id: ChatTg)
