package net.maffoo.jsonquote.json4s

import org.json4s._
import org.json4s.native.JsonMethods._

object Compat {
  def compactRender(json: JValue): String = compact(render(json))
}
