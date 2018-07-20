package jp.co.soramitsu.nakayoshi

import com.typesafe.scalalogging.Logger

trait Loggable {
  val l: Logger = Logger(getClass)
}
