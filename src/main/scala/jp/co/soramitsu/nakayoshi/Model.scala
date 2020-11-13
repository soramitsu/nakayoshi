package jp.co.soramitsu.nakayoshi

import jp.co.soramitsu.nakayoshi.Types._

case class GitterGroup(id: String, name: String, uri: String)
case class GitterRoom(id: String, name: String, url: String, groupId: Option[String])
case class TelegramChat(title: String, username: Option[String])
case class Connection(tgId: Option[ChatTg], gtId: Option[ChatGt], rcId: Option[ChatRc])

object Types {
  type ChatTg = Long
  type ChatGt = String
  type ChatRc = String
  type MsgTg  = Long
  type MsgGt  = String
  type MsgRc  = String
}
