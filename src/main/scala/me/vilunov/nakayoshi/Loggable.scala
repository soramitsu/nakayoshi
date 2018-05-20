package me.vilunov.nakayoshi

import com.typesafe.scalalogging.Logger

trait Loggable {
  lazy val l: Logger = Logger(this.getClass.getName)
}
