package net.maffoo.jsonquote.spray

import net.maffoo.jsonquote.Parser
import scala.collection.immutable.ListMap
import _root_.spray.json._

object Parse extends Parser[JsValue, (String, JsValue)] {
  def makeObject(fields: Iterable[(String, JsValue)]): JsValue = JsObject(ListMap(fields.toSeq: _*)) // preserve iteration order until splicing is done
  def makeArray(elements: Iterable[JsValue]): JsValue = JsArray(elements.toSeq: _*)
  def makeNumber(n: BigDecimal): JsValue = JsNumber(n)
  def makeString(s: String): JsValue = JsString(s)
  def makeBoolean(b: Boolean): JsValue = JsBoolean(b)
  def makeNull(): JsValue = JsNull
  def makeSpliceValue(): JsValue = SpliceValue()
  def makeSpliceValues(): JsValue = SpliceValues()

  def makeField(k: String, v: JsValue): (String, JsValue) = (k, v)
  def makeSpliceField(): (String, JsValue) = SpliceField()
  def makeSpliceFields(): (String, JsValue) = SpliceFields()
  def makeSpliceFieldNameOpt(): (String, JsValue) = SpliceFieldNameOpt()
  def makeSpliceFieldName(v: JsValue): (String, JsValue) = SpliceFieldName(v)
  def makeSpliceFieldOpt(k: String): (String, JsValue) = SpliceFieldOpt(k)
}
