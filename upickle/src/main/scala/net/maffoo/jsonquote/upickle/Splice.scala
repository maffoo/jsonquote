package net.maffoo.jsonquote.upickle

import ujson._

import scala.util.Random

// Sentinel values to mark splice points in the json AST.
// Because ujson uses Map to represent json objects, we use unique
// random keys beginning with a known prefix to mark field splice points.

class Sentinel[A <: AnyRef](inst: A) {
  def apply(): A = inst
  def unapply(a: A): Boolean = a == inst
}

object SpliceValue  extends Sentinel[Value](Obj("__splice_value__" -> Null))
object SpliceValues extends Sentinel[Value](Obj("__splice_values__" -> Null))

object SpliceField {
  def apply(): (String, Value) = ("__splice_field__%016X".format(Random.nextLong), Null)
  def unapply(a: (String, Value)): Boolean = a match {
    case (f, Null) if f.startsWith("__splice_field__") => true
    case _ => false
  }
}

object SpliceFields {
  def apply(): (String, Value) = ("__splice_fields__%016X".format(Random.nextLong), Null)
  def unapply(a: (String, Value)): Boolean = a match {
    case (f, Null) if f.startsWith("__splice_fields__") => true
    case _ => false
  }
}

object SpliceFieldNameOpt {
  def apply(): (String, Value) = ("__splice_field_name_opt__%016X".format(Random.nextLong), Null)
  def unapply(f: (String, Value)): Boolean = f match {
    case (f, Null) if f.startsWith("__splice_field_name_opt__") => true
    case _ => false
  }
}

object SpliceFieldName {
  def apply(x: Value): (String, Value) = ("__splice_field_name__%016X".format(Random.nextLong), x)
  def unapply(f: (String, Value)): Option[Value] = f match {
    case (f, x) if f.startsWith("__splice_field_name__") => Some(x)
    case _ => None
  }
}

object SpliceFieldOpt {
  def apply(k: String): (String, Null) = (k, null)
  def unapply(f: (String, Value)): Option[String] = f match {
    case (k, null) => Some(k)
    case _ => None
  }
}
