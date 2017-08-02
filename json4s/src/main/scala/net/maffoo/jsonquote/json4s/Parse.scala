package net.maffoo.jsonquote.json4s

import org.json4s._
import net.maffoo.jsonquote.Parser

object Parse extends Parser[JValue, JField] {
  def makeObject(fields: Iterable[JField]): JValue = JObject(fields.toList)
  def makeArray(elements: Iterable[JValue]): JValue = JArray(elements.toList)
  def makeNumber(n: BigDecimal): JValue = if (n.isWhole) JInt(n.toBigInt) else JDouble(n.toDouble)
  def makeString(s: String): JValue = JString(s)
  def makeBoolean(b: Boolean): JValue = JBool(b)
  def makeNull(): JValue = JNull
  def makeSpliceValue(): JValue = SpliceValue()
  def makeSpliceValues(): JValue = SpliceValues()

  def makeField(k: String, v: JValue): JField = JField(k, v)
  def makeSpliceField(): JField = SpliceField()
  def makeSpliceFields(): JField = SpliceFields()
  def makeSpliceFieldNameOpt: JField = SpliceFieldNameOpt()
  def makeSpliceFieldName(v: JValue): JField = SpliceFieldName(v)
  def makeSpliceFieldOpt(k: String): JField = SpliceFieldOpt(k)
}
