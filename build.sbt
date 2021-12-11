import com.typesafe.sbt.packager.docker._

scalacOptions += "-Ypartial-unification"

name := "nakayoshi"

version := "0.1.3"

scalaVersion := "2.12.12"

mainClass := Some("jp.co.soramitsu.nakayoshi.Main")

enablePlugins(JavaAppPackaging)
enablePlugins(DockerPlugin)
enablePlugins(AshScriptPlugin)

val log4j    = "2.15.0"
val akka     = "2.6.13"
val sttp     = "1.7.2"
val telegram = "4.4.0-RC2-SNAPSHOT"

libraryDependencies ++= Seq(
  "com.bot4s"                  %% "telegram-core"     % telegram,
  "com.bot4s"                  %% "telegram-akka"     % telegram,
  "com.typesafe"                % "config"            % "1.3.+",
  "org.json4s"                 %% "json4s-native"     % "3.5.3",
  "com.keysolutions"            % "java-ddp-client"   % "1.0.0.7" exclude ("org.slf4j", "slf4j-simple"),
  // HTTP Client
  "com.softwaremill.sttp"      %% "core"              % sttp,
  "com.softwaremill.sttp"      %% "akka-http-backend" % sttp,
  // Database
  "com.typesafe.slick"         %% "slick"             % "3.2.1",
  "org.xerial"                  % "sqlite-jdbc"       % "3.21.0",
  // Logging dependencies
  "com.typesafe.scala-logging" %% "scala-logging"     % "3.9.+",
  "ch.qos.logback"              % "logback-classic"   % "1.2.+",
  // Akka
  "com.typesafe.akka"          %% "akka-actor"        % akka,
  "com.typesafe.akka"          %% "akka-stream"       % akka,
  "com.typesafe.akka"          %% "akka-slf4j"        % akka,
  "com.typesafe.akka"          %% "akka-http"         % "10.0.11",
  // Test
  "org.scalatest"              %% "scalatest"         % "3.0.5" % Test,
  // Cats
  "org.typelevel"              %% "cats-core"         % "2.1.1"
)

assemblyJarName in assembly := name.value + ".jar"
assemblyMergeStrategy in assembly := {
  case "logback.xml"      => MergeStrategy.first
  case "application.conf" => MergeStrategy.concat
  case x                  => (assemblyMergeStrategy in assembly).value(x)
}

dockerUpdateLatest := true

dockerCommands := Seq(
  Cmd("FROM", "openjdk:jre-alpine"),
  Cmd("WORKDIR", "/opt/docker"),
  Cmd("ADD", "--chown=daemon:daemon", "opt", "/opt"),
  ExecCmd("RUN", "mkdir", "-p", "/opt/docker/files", "/opt/docker/settings"),
  ExecCmd("VOLUME", "/opt/docker/files", "/opt/docker/settings"),
  ExecCmd("RUN", "chown", "daemon:daemon", "/opt/docker/files/", "/opt/docker/settings/"),
  Cmd("USER", "daemon"),
  ExecCmd("ENTRYPOINT", "bin/" + name.value),
  Cmd("EXPOSE", "8080"),
  ExecCmd("CMD")
)

lazy val buildTelegramLibrarySnapshot =
  taskKey[Unit]("Downloads com.bot4s.telegram-core and -akka and builds them. Expects mill and git to be installed.")

buildTelegramLibrarySnapshot := {
  import scala.sys.process._
  import java.io.File

  val log       = streams.value.log
  "rm -r -f .build" ! log
  "mkdir .build" ! log
  Process("git" :: "clone" :: "https://github.com/bot4s/telegram.git" :: Nil, new File("./.build")) ! log
  val buildPath = new File("./.build/telegram")
  Process("git" :: "checkout" :: "c40d679be5865d1a5caa897e0c0f2307922a0cca" :: Nil, buildPath) ! log
  Process("sed" :: "-i" :: "s/4.4.0-RC2/4.4.0-RC2-SNAPSHOT/g" :: "build.sc" :: Nil, buildPath) ! log
  Process("mill" :: "core.jvm[2.12.13].publishLocal" :: Nil, Some(buildPath)) ! log
  Process("mill" :: "akka.jvm[2.12.13].publishLocal" :: Nil, Some(buildPath)) ! log
}
