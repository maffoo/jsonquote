package net.maffoo.jsonquote.lift

import net.liftweb.json._

object Compat {
  def compactRender(json: JValue): String = compact(render(json))
}
