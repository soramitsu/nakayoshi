import com.typesafe.sbt.packager.docker._

name := "nakayoshi"

version := "0.1"

scalaVersion := "2.12.5"

mainClass := Some("me.vilunov.nakayoshi.Main")

enablePlugins(JavaAppPackaging)
enablePlugins(DockerPlugin)
enablePlugins(AshScriptPlugin)

val log4j = "2.10.0"
val akka = "2.5.7"

libraryDependencies ++= Seq(
  "info.mukel" %% "telegrambot4s" % "3.0.14",
  "com.typesafe" % "config" % "1.3.+",
  "org.json4s" %% "json4s-native" % "3.5.3",
  "com.keysolutions" % "java-ddp-client" % "1.0.0.7" exclude("org.slf4j", "slf4j-simple"),
  // HTTP Client
  "com.softwaremill.sttp" %% "core" % "1.1.9",
  "com.softwaremill.sttp" %% "akka-http-backend" % "1.1.9",
  // Database
  "com.typesafe.slick" %% "slick" % "3.2.1",
  "org.xerial" % "sqlite-jdbc" % "3.21.0",
  // Logging dependencies
  "com.typesafe.scala-logging" %% "scala-logging"   % "3.9.+",
  "ch.qos.logback"             %  "logback-classic" % "1.2.+",
  // Akka
  "com.typesafe.akka" %% "akka-actor" % akka,
  "com.typesafe.akka" %% "akka-stream" % akka,
  "com.typesafe.akka" %% "akka-slf4j" % akka,
  "com.typesafe.akka" %% "akka-http" % "10.0.11",
  // Test
  "org.scalatest" %% "scalatest" % "3.0.5" % Test
)

assemblyJarName in assembly := name.value + ".jar"
assemblyMergeStrategy in assembly := {
  case "logback.xml" => MergeStrategy.first
  case "application.conf"  => MergeStrategy.concat
  case x => (assemblyMergeStrategy in assembly).value(x)
}

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
