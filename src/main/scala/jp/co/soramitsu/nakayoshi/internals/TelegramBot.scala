package jp.co.soramitsu.nakayoshi.internals

import com.typesafe.scalalogging.Logger
import com.bot4s.telegram.future.BotExecutionContext
import com.bot4s.telegram.api._
import com.bot4s.telegram.api.declarative.{Commands, CommandFilterMagnet, Action}
import com.bot4s.telegram.clients.AkkaHttpClient
import com.bot4s.telegram.models.{ChatType, Message}

import cats.syntax.functor._
import cats.instances.future._
import cats.MonadError

import scala.concurrent.Future

trait TelegramBot
    extends BotBase[Future] with AkkaImplicits with BotExecutionContext with Commands[Future] with Loggable {
  def token: String
  val host                                    = "api.telegram.org"
  override val monad                          = MonadError[Future, Throwable]
  override val client: RequestHandler[Future] = new AkkaHttpClient(token, host)
  val admins: Set[String]

  def adminCmd(cmds: CommandFilterMagnet)(func: Action[Future, Message]): Unit =
    onCommand(cmds) { implicit msg =>
      if (msg.chat.`type` == ChatType.Private) {
        if (admins.contains(msg.from.get.username.get)) func(msg).void
        else reply("Only the admins can access the bot's commands").void
      } else {
        Future.unit
      }
    }
}
