package net.maffoo.jsonquote.play

import _root_.play.api.libs.json._

// Sentinel values to mark splice points in the json AST.
// When pattern matching to detect these, we use reference equality instead
// of value equality, because we want to treat them specially even though
// they may be valid values.

class Sentinel[A <: AnyRef](inst: A) {
  def apply() = inst
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

/**
 * Transform a json AST by replacing the marked splice points with the given values.
 */
object Splice {

  def apply(ast: JsValue, values: Iterable[Any] = Iterable.empty): JsValue = splice(ast, values.iterator)

  def splice(ast: JsValue, values: Iterator[Any]): JsValue = ast match {
    case SpliceValue() => spliceValue(values.next)
    case SpliceValues() => sys.error("cannot splice values at top level")

    case JsObject(members) =>
      JsObject(members.flatMap {
        case SpliceField()      => Seq(spliceField(values.next))
        case SpliceFields()     => spliceFields(values.next)
        case SpliceFieldName(v) => Seq(spliceFieldName(values.next) -> splice(v, values))
        case (k, v)             => Seq(k -> splice(v, values))
      }.toIndexedSeq)

    case JsArray(elements) =>
      JsArray(elements.flatMap {
        case SpliceValue()  => Seq(spliceValue(values.next))
        case SpliceValues() => spliceValues(values.next)
        case e              => Seq(splice(e, values))
      }.toIndexedSeq)

    case value => value
  }

  def spliceField(value: Any): (String, JsValue) = value match {
    case (k, v) => spliceFieldName(k) -> spliceValue(v)
  }

  def spliceValues(value: Any): Iterable[JsValue] = value match {
    case xs: Iterable[_] => xs.map(spliceValue)
    case xs: Option[_]   => xs.map(spliceValue)
  }

  def spliceFields(value: Any): Iterable[(String, JsValue)] = value match {
    case xs: Iterable[_] => xs.map(spliceField)
    case xs: Option[_]   => xs.map(spliceField)
  }

  def spliceFieldName(value: Any): String = value.toString

  def spliceValue(value: Any): JsValue = value match {
    case json: JsValue => json
    case s: String => JsString(s)
    case n: Int => JsNumber(n)
    case d: Double => JsNumber(d)
  }
}
