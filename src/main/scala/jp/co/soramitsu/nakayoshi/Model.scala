package jp.co.soramitsu.nakayoshi

import Types._

case class GitterGroup(id: String, name: String, uri: String)
case class GitterRoom(id: String, name: String, url: String, groupId: Option[String])
case class GitterMessage(chatId: String, userId: String, username: String, userUrl: String, content: String)
case class RocketchatMessage(chatId: String, nickname: String, content: String)
case class TelegramChat(title: String, username: Option[String])
case class Connection(tgId: Option[IdTg], gtId: Option[IdGt], rcId: Option[IdRc])

object Types {
  type IdTg = Long
  type IdGt = String
  type IdRc = String
}
