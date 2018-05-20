package me.vilunov.nakayoshi

import akka.actor.ActorRef
import info.mukel.telegrambot4s.models.MessageEntity

// For starting actors
case class MsgRun(router: ActorRef)

// When a message is received, sent to the router
case class MsgFromTelegram(chatId: Long, user: String, msg: Option[(String, Seq[MessageEntity])], fileUrl: Option[String], fwd: Option[String])
//GitterMessage from Model.scala is used for doing the same with Gitter

case class MsgAddTgChat(chatId: Long, chat: TelegramChat) // Adding a Telegram chat to list
case class MsgConnect(connection: Connection) // Adding a connection to router

case class MsgGitterListen(id: String) // Initiate a listening connection to a Gitter room

// For sending messages
case class MsgSendGitter(id: String, text: String) // Not only Gitter but Rocketchat also :)
case class MsgSendTelegram(id: Long, text: String)

case class MsgRcJoin(name: String)
case class MsgRcListen(id: String)
