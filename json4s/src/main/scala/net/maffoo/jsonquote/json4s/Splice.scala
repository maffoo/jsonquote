package net.maffoo.jsonquote.json4s

import org.json4s._

// Sentinel values to mark splice points in the json AST.
// When pattern matching to detect these, we use reference equality instead
// of value equality, because we want to treat them specially even though
// they may be valid values.

class Sentinel[A <: AnyRef](inst: A) {
  def apply() = inst
  def unapply(a: A): Boolean = a eq inst
}

object SpliceValue  extends Sentinel[JValue](new JObject(Nil))
object SpliceValues extends Sentinel[JValue](new JObject(Nil))

object SpliceField  extends Sentinel[JField](new JField("", JNull))
object SpliceFields extends Sentinel[JField](new JField("", JNull))

object SpliceFieldNameOpt extends Sentinel[JField](new JField("", JNull))

object SpliceFieldName {
  def apply(x: JValue) = JField(null, x)
  def unapply(f: JField): Option[JValue] = f match {
    case JField(null, x) => Some(x)
    case _ => None
  }
}

object SpliceFieldOpt {
  def apply(k: String) = JField(k, null)
  def unapply(f: JField): Option[String] = f match {
    case JField(k, null) => Some(k)
    case _ => None
  }
}
