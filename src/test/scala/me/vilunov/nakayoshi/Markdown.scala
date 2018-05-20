package me.vilunov.nakayoshi

import org.scalatest._

import MarkdownConverter._

class Markdown extends FlatSpec with Matchers {
  "Markdown converter" should "escape underscores" in {
    gt2tg("@u_menya_slozhniy_nick") should be ("@u\\_menya\\_slozhniy\\_nick")
  }
}
