package net.maffoo.jsonquote

trait Parser[V, F] {
  import Token._
  import Util._

  def apply(parts: Seq[String]): V = apply(Lex(parts))

  def apply(it: Iterator[Token]): V = {
    val result = parseValue(it.buffered)
    expect[Token](EOF)(it)
    result
  }

  def parseValue(implicit it: BufferedIterator[Token]): V = {
    it.head match {
      case OBJECT_START => parseObject(it)
      case ARRAY_START  => parseArray(it)
      case NUMBER(n)    => it.next(); makeNumber(n)
      case STRING(s)    => it.next(); makeString(s)
      case TRUE         => it.next(); makeBoolean(true)
      case FALSE        => it.next(); makeBoolean(false)
      case NULL         => it.next(); makeNull()
      case SPLICE       => it.next(); makeSpliceValue()
      case tok          => sys.error(s"unexpected token: $tok")
    }
  }

  def parseArray(implicit it: BufferedIterator[Token]): V = {
    expect[Token](ARRAY_START)
    val elements = if (it.head == ARRAY_END) Nil else parseElements
    expect[Token](ARRAY_END)
    makeArray(elements)
  }

  def parseElements(implicit it: BufferedIterator[Token]): Seq[V] = {
    val members = Seq.newBuilder[V]
    def advance(): Unit = {
      if (it.head == REPEAT) {
        it.next()
        expect[Token](SPLICE)
        members += makeSpliceValues()
      } else {
        members += parseValue
      }
    }
    advance()
    while (it.head != ARRAY_END) {
      expect[Token](COMMA)
      advance()
    }
    members.result
  }

  def parseObject(implicit it: BufferedIterator[Token]): V = {
    expect[Token](OBJECT_START)
    val members = if (it.head == OBJECT_END) Nil else parseMembers
    expect[Token](OBJECT_END)
    makeObject(members)
  }

  def parseMembers(implicit it: BufferedIterator[Token]): Seq[F] = {
    val members = Seq.newBuilder[F]
    def advance(): Unit = {
      if (it.head == REPEAT) {
        it.next()
        expect[Token](SPLICE)
        members += makeSpliceFields()
      } else {
        members += parsePair
      }
    }
    advance()
    while (it.head != OBJECT_END) {
      expect[Token](COMMA)
      advance()
    }
    members.result
  }

  def parsePair(implicit it: BufferedIterator[Token]): F = {
    it.next() match {
      case STRING(k) =>
        expect[Token](COLON)
        makeField(k, parseValue)

      case IDENT(k) =>
        expect[Token](COLON)
        makeField(k, parseValue)

      case SPLICE =>
        it.head match {
          case COLON =>
            it.next()
            makeSpliceFieldName(parseValue)

          case _ =>
            makeSpliceField()
        }

      case tok =>
        sys.error(s"expected field but got $tok")
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
}
