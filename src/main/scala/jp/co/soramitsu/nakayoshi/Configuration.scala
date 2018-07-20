package jp.co.soramitsu.nakayoshi

import scala.collection.JavaConverters._
import java.io.File

import com.typesafe.config.{Config, ConfigFactory}

object Configuration {
  private val conf: Config = {
    val appConf = ConfigFactory.load()
    val localConf = new File(Strings.configPath)
    if (!localConf.isFile) appConf
    else ConfigFactory.parseFile(localConf).withFallback(appConf)
  }

  lazy val tgToken: String = conf.getString("telegram.token")
  lazy val tgAdmins: Set[String] = conf.getStringList("telegram.admin").asScala.toSet
  lazy val gtToken: String = conf.getString("gitter.token")
  lazy val gtUsername: String = "/" + conf.getString("gitter.username")
  lazy val rcPath: String = conf.getString("rocketchat.path")
  lazy val rcUser: String = conf.getString("rocketchat.user")
  lazy val rcPassword: String = conf.getString("rocketchat.password")

  lazy val httpEnabled: Boolean = conf.getBoolean("http.enabled")
  lazy val httpInterface: String = conf.getString("http.interface")
  lazy val httpPort: Int = conf.getInt("http.port")
  lazy val hostname: String = conf.getString("public-hostname")
}

object Strings {
  val dataPath = "data/"
  val configPath = dataPath + "local.conf"
  val publicPath = dataPath + "public/"
}
