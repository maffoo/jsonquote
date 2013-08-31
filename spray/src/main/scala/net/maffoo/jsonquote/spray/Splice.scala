package net.maffoo.jsonquote.spray

import scala.util.Random
import spray.json._

// Sentinel values to mark splice points in the json AST.
// Because spray uses Map to represent json objects, we use unique
// random keys beginning with a known prefix to mark field splice points.

class Sentinel[A <: AnyRef](inst: A) {
  def apply() = inst
  def unapply(a: A): Boolean = a == inst
}

object SpliceValue  extends Sentinel[JsValue](JsObject(Map("__splice_value__" -> JsNull)))
object SpliceValues extends Sentinel[JsValue](JsObject(Map("__splice_values__" -> JsNull)))

object SpliceField {
  def apply(): (String, JsValue) = ("__splice_field__%016X".format(Random.nextLong), JsNull)
  def unapply(a: (String, JsValue)): Boolean = a match {
    case (f, JsNull) if f.startsWith("__splice_field__") => true
    case _ => false
  }
}

object SpliceFields {
  def apply(): (String, JsValue) = ("__splice_fields__%016X".format(Random.nextLong), JsNull)
  def unapply(a: (String, JsValue)): Boolean = a match {
    case (f, JsNull) if f.startsWith("__splice_fields__") => true
    case _ => false
  }
}

object SpliceFieldName {
  def apply(x: JsValue) = ("__splice_field_name__%016X".format(Random.nextLong), x)
  def unapply(f: (String, JsValue)): Option[JsValue] = f match {
    case (f, x) if f.startsWith("__splice_field_name__") => Some(x)
    case _ => None
  }
}
