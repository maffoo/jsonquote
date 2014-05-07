package net.maffoo.jsonquote.play

import _root_.play.api.libs.json._

// Sentinel values to mark splice points in the json AST.
// When pattern matching to detect these, we use reference equality instead
// of value equality, because we want to treat them specially even though
// they may be valid values.

class Sentinel[A <: AnyRef](inst: A) {
  def apply(): A = inst
  def unapply(a: A): Boolean = a eq inst
}

object SpliceValue  extends Sentinel[JsValue](new JsObject(Nil))
object SpliceValues extends Sentinel[JsValue](new JsObject(Nil))

object SpliceField  extends Sentinel[(String, JsValue)](("", new JsObject(Nil)))
object SpliceFields extends Sentinel[(String, JsValue)](("", new JsObject(Nil)))

object SpliceFieldName {
  def apply(x: JsValue) = (null, x)
  def unapply(f: (String, JsValue)): Option[JsValue] = f match {
    case (null, x) => Some(x)
    case _ => None
  }
}

object SpliceFieldOpt {
  def apply(k: String) = (k, null)
  def unapply(f: (String, JsValue)): Option[String] = f match {
    case (k, null) => Some(k)
    case _ => None
  }
}
