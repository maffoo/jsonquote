package net.maffoo.jsonquote.upickle

import net.maffoo.jsonquote.Parser
import ujson._

object Parse extends Parser[Value, (String, Value)] {
  def makeObject(fields: Iterable[(String, Value)]): Value = Obj.from(fields)
  def makeArray(elements: Iterable[Value]): Value = Arr.from(elements)
  def makeNumber(n: BigDecimal): Value = Num(n.toDouble)
  def makeNumber(n: Double): Value = Num(n)
  def makeString(s: String): Value = Str(s)
  def makeBoolean(b: Boolean): Value = Bool(b)
  def makeNull(): Value = Null
  def makeSpliceValue(): Value = SpliceValue()
  def makeSpliceValues(): Value = SpliceValues()

  def makeField(k: String, v: Value): (String, Value) = (k, v)
  def makeSpliceField(): (String, Value) = SpliceField()
  def makeSpliceFields(): (String, Value) = SpliceFields()
  def makeSpliceFieldNameOpt(): (String, Value) = SpliceFieldNameOpt()
  def makeSpliceFieldName(v: Value): (String, Value) = SpliceFieldName(v)
  def makeSpliceFieldOpt(k: String): (String, Value) = SpliceFieldOpt(k)
}
