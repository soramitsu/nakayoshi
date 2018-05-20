package me.vilunov.nakayoshi

import com.typesafe.scalalogging.Logger
import info.mukel.telegrambot4s.api._
import info.mukel.telegrambot4s.clients.AkkaClient

/**
  * This trait replaces [[info.mukel.telegrambot4s.api.TelegramBot]] in order to use non-global ExecutionContext
  */
trait TelegramBot extends BotBase with AkkaImplicits with BotExecutionContext {
  override val logger = Logger(getClass)
  override val client: RequestHandler = new AkkaClient(token)
}
