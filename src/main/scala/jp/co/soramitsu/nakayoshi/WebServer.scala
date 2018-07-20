package jp.co.soramitsu.nakayoshi

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatchers.Remaining
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.ContentTypeResolver.Default
import akka.stream.Materializer

import scala.concurrent.{ExecutionContext, Future}

object WebServer {
  private def route(implicit context: ExecutionContext): Route =
    get {
      path(Remaining) { addr: String =>
        getFromFile(Strings.publicPath + addr)
      }
    }

  def start()(implicit system: ActorSystem, fm: Materializer, context: ExecutionContext): Future[ServerBinding] =
    Http().bindAndHandle(route, Configuration.httpInterface, Configuration.httpPort)
}
