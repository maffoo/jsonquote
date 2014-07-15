package net.maffoo.jsonquote.literal

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

  /**
   * Quote strings for inclusion as JSON strings.
   */
  def quoteString (s : String) : String = s.flatMap {
    case '"'  => """\""""
    case '\\' => """\\"""
    case '/'  => """\/"""
    case '\b' => """\b"""
    case '\f' => """\f"""
    case '\n' => """\n"""
    case '\r' => """\r"""
    case '\t' => """\t"""
    case c if c.isControl => f"\\u$c%04x"
    case c => c.toString
  }
}
