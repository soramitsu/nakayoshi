package jp.co.soramitsu.nakayoshi

import info.mukel.telegrambot4s.models.{MessageEntity, MessageEntityType}
import info.mukel.telegrambot4s.models.MessageEntityType.MessageEntityType

import scala.util.matching.Regex

object MarkdownConverter {
  def tg2gt(text: String, entities: Seq[MessageEntity]): String = {
    def seq(t: MessageEntityType): String = t match {
      case MessageEntityType.Bold => "**"
      case MessageEntityType.Code => "`"
      case MessageEntityType.Pre => "```\n"
      case MessageEntityType.Italic => "*"
      case _ => ""
    }
    entities.flatMap { it =>
      Seq((it.offset, seq(it.`type`)), (it.length + it.offset, seq(it.`type`)))
    }.sortBy(_._1).foldRight(text) { (it, text) =>
      val (l, r) = text.splitAt(it._1)
      l + it._2 + r
    }
  }
  def tg2rc(text: String, entities: Seq[MessageEntity]): String = {
    def seq(t: MessageEntityType): String = t match {
      case MessageEntityType.Bold => "*"
      case MessageEntityType.Code => "`"
      case MessageEntityType.Pre => "`\n"
      case MessageEntityType.Italic => "_"
      case _ => ""
    }
    entities.flatMap { it =>
      Seq((it.offset, seq(it.`type`)), (it.length + it.offset, seq(it.`type`)))
    }.sortBy(_._1).foldRight(text) { (it, text) =>
      val (l, r) = text.splitAt(it._1)
      l + it._2 + r
    }
  }

  private def applyPipe(pipe: Seq[(Regex, String)], text: String): String =
    pipe.foldLeft(text) { (text, op) => op._1.replaceAllIn(text, op._2) }

  private lazy val pipe_gt2tg = Seq(
    ("_".r, "\\\\_"), // escape
    ("(?<!\\*)\\*([^\\*]+)\\*(?!\\*)".r, "_$1_"), // one star each side, italic
    ("(?<!\\*)\\*\\*([^\\*]+)\\*\\*(?!\\*)".r, "*$1*"), // two stars each side, bold
    ("(?<!\\*)\\*\\*\\*([^\\*]+)\\*\\*\\*(?!\\*)".r, "*_$1_*"), // three start, italic + bold
    ("```".r, "`")
  )
  def gt2tg(text: String): String = applyPipe(pipe_gt2tg, text)

  private lazy val pipe_gt2rc = Seq(
    ("(?<!\\*)\\*([^\\*]+)\\*(?!\\*)".r, "_$1_"), // one star each side, italic
    ("(?<!\\*)\\*\\*([^\\*]+)\\*\\*(?!\\*)".r, "*$1*"), // two stars each side, bold
    ("(?<!\\*)\\*\\*\\*([^\\*]+)\\*\\*\\*(?!\\*)".r, "*_$1_*"), // three start, italic + bold
    ("```\n".r, "```")
  )
  def gt2rc(text: String): String = applyPipe(pipe_gt2rc, text)

  private lazy val pipe_rc2gt = Seq(
    ("(?<!\\*)\\*([^\\*]+)\\*(?!\\*)".r, "**$1**"), // one star to two stars, bold
    ("_([^_]+)_".r, "*$1*"), // lower underscore to one star, italic
    ("```".r, "```\n")
  )
  def rc2gt(text: String): String = applyPipe(pipe_rc2gt, text)

  def rc2tg(text: String): String = text

}
