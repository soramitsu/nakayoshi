package me.vilunov.nakayoshi

import akka.actor.{ActorSystem, Props}
import akka.stream.ActorMaterializer

import scala.concurrent.ExecutionContextExecutor

object Main extends App with Loggable {
  implicit val system: ActorSystem = ActorSystem("bridge")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val execContext: ExecutionContextExecutor = system.dispatcher

  Storage.create()

  val botTelegram = system.actorOf(Props(
    new BotTg(Configuration.tgToken, Configuration.fileFolder, Configuration.tgAdmins)), "botTelegram")
  val botGitter = system.actorOf(Props(
    new BotGitter(Configuration.gtToken)), "botGitter")
  val botRocketchat = system.actorOf(Props(
    new BotRocketchat(Configuration.rcPath, Configuration.rcUser, Configuration.rcPassword)), "botRocketchat")
  val router = system.actorOf(Props(
    new ActorMsgRouter(botTelegram, botGitter, botRocketchat, Configuration.hostname)), "router")

  router ! 'updateConnsFromDB

  if(Configuration.httpEnabled) WebServer.start()
}
