package jp.co.soramitsu.nakayoshi.internals

import com.typesafe.scalalogging.Logger
import info.mukel.telegrambot4s.api._
import info.mukel.telegrambot4s.api.declarative.{Commands, ToCommand}
import info.mukel.telegrambot4s.clients.AkkaClient
import info.mukel.telegrambot4s.models.{ChatType, Message}

trait TelegramBot extends BotBase with AkkaImplicits with BotExecutionContext with Commands with Loggable {
  val host = "api.telegram.org"
  override val logger: Logger = l
  override val client: RequestHandler = new AkkaClient(token, host)
  val admins: Set[String]

  def adminCmd[T : ToCommand](cmds: T*)(func: Message => Unit): Unit =
    onCommand(cmds: _*) { implicit msg =>
      if (msg.chat.`type` == ChatType.Private) {
        if (admins.contains(msg.from.get.username.get)) func(msg)
        else reply("Only the admins can access the bot's commands")
      }
    }
}
