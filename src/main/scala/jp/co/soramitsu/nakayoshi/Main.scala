package jp.co.soramitsu.nakayoshi

import akka.actor.{ActorSystem, Props}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp.SttpBackend
import com.softwaremill.sttp.akkahttp.AkkaHttpBackend

import scala.concurrent.{ExecutionContext, Future}

object Main extends App with Loggable {
  implicit val system: ActorSystem = ActorSystem("bridge")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContext = system.dispatcher
  implicit val sttpBackend: SttpBackend[Future, Source[ByteString, Any]] = AkkaHttpBackend.usingActorSystem(system)

  Storage.create()

  val botTelegram = system.actorOf(Props(
    new BotTg(Configuration.tgToken, Configuration.tgAdmins)), "botTelegram")
  val botGitter = system.actorOf(Props(
    new BotGitter(Configuration.gtToken)), "botGitter")
  val botRocketchat = system.actorOf(Props(
    new BotRocketchat(Configuration.rcPath, Configuration.rcUser, Configuration.rcPassword)), "botRocketchat")
  val router = system.actorOf(Props(
    new ActorMsgRouter(botTelegram, botGitter, botRocketchat, Configuration.hostname)), "router")

  router ! 'updateConnsFromDB

  if (Configuration.httpEnabled) WebServer.start()
}
