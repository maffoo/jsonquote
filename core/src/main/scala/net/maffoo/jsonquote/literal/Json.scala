package net.maffoo.jsonquote.literal

import scala.util.parsing.json.JSONFormat.quoteString

class Json private[jsonquote] (val s: String) extends AnyVal {
  override def toString = s
}

object Json {
  /**
   * Parse a json string at runtime, marking it as valid with the Json value class.
   */
  def apply(s: String): Json = Parse(Seq(s)) match {
    case Seq(Chunk(s)) => new Json(s)
  }
}
