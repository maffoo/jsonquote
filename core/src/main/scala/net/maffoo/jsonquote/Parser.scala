package net.maffoo.jsonquote

trait Parser[V, F] {
  import Token._
  import Util._

  def apply(parts: Seq[String]): V = apply(Lex(parts))

  def apply(it: Iterator[(Token, Pos)]): V = {
    val result = parseValue(it.buffered)
    expect[Token](EOF)(it)
    result
  }

  def expect[A](a: A)(implicit it: Iterator[(A, Pos)]): Unit = {
    val (next, pos) = it.next()
    if (next != a) throw JsonError(s"expected $a but got $next", pos)
  }

  def parseValue(implicit it: BufferedIterator[(Token, Pos)]): V = {
    val (tok, pos) = it.head
    tok match {
      case OBJECT_START => parseObject(it)
      case ARRAY_START  => parseArray(it)
      case NUMBER(n)    => it.next(); makeNumber(n)
      case STRING(s)    => it.next(); makeString(s)
      case TRUE         => it.next(); makeBoolean(true)
      case FALSE        => it.next(); makeBoolean(false)
      case NULL         => it.next(); makeNull()
      case SPLICE       => it.next(); makeSpliceValue()
      case tok          => throw JsonError(s"unexpected token: $tok", pos)
    }
  }

  def parseArray(implicit it: BufferedIterator[(Token, Pos)]): V = {
    expect[Token](ARRAY_START)
    val elements = if (it.head._1 == ARRAY_END) Nil else parseElements
    expect[Token](ARRAY_END)
    makeArray(elements)
  }

  def parseElements(implicit it: BufferedIterator[(Token, Pos)]): Seq[V] = {
    val members = Seq.newBuilder[V]
    def advance(): Unit = {
      if (it.head._1 == REPEAT) {
        it.next()
        expect[Token](SPLICE)
        members += makeSpliceValues()
      } else {
        members += parseValue
      }
    }
    advance()
    while (it.head._1 != ARRAY_END) {
      expect[Token](COMMA)
      advance()
    }
    members.result
  }

  def parseObject(implicit it: BufferedIterator[(Token, Pos)]): V = {
    expect[Token](OBJECT_START)
    val members = if (it.head._1 == OBJECT_END) Nil else parseMembers
    expect[Token](OBJECT_END)
    makeObject(members)
  }

  def parseMembers(implicit it: BufferedIterator[(Token, Pos)]): Seq[F] = {
    val members = Seq.newBuilder[F]
    def advance(): Unit = {
      if (it.head._1 == REPEAT) {
        it.next()
        expect[Token](SPLICE)
        members += makeSpliceFields()
      } else {
        members += parsePair
      }
    }
    advance()
    while (it.head._1 != OBJECT_END) {
      expect[Token](COMMA)
      advance()
    }
    members.result
  }

  def parsePair(implicit it: BufferedIterator[(Token, Pos)]): F = {
    val (tok, pos) = it.next()
    tok match {
      case STRING(k) =>
        expect[Token](COLON)
        it.head._1 match {
          case OPTIONAL =>
            it.next()
            expect[Token](SPLICE)
            makeSpliceFieldOpt(k)

          case _ => makeField(k, parseValue)
        }

      case IDENT(k) =>
        expect[Token](COLON)
        it.head._1 match {
          case OPTIONAL =>
            it.next()
            expect[Token](SPLICE)
            makeSpliceFieldOpt(k)

          case _ => makeField(k, parseValue)
        }

      case SPLICE =>
        it.head._1 match {
          case COLON =>
            it.next()
            makeSpliceFieldName(parseValue)

          case _ =>
            makeSpliceField()
        }

      case tok =>
        throw JsonError(s"expected field but got $tok", pos)
    }
  }

  // abstract methods for constructing AST objects from parse results
  def makeObject(fields: Iterable[F]): V
  def makeArray(elements: Iterable[V]): V
  def makeNumber(n: BigDecimal): V
  def makeString(s: String): V
  def makeBoolean(b: Boolean): V
  def makeNull(): V
  def makeSpliceValue(): V
  def makeSpliceValues(): V

  def makeField(k: String, v: V): F
  def makeSpliceField(): F
  def makeSpliceFields(): F
  def makeSpliceFieldName(v: V): F
  def makeSpliceFieldOpt(k: String): F
}
