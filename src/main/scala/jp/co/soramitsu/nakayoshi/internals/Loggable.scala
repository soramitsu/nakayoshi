package jp.co.soramitsu.nakayoshi.internals

import com.typesafe.scalalogging.Logger

trait Loggable {
  val l: Logger = Logger(getClass)
}
