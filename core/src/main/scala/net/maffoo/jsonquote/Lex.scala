package net.maffoo.jsonquote

sealed trait Token
object Token {
  abstract class Literal(val value: String) extends Token {
    override def toString = s"'$value'"
  }

  case class STRING(value: String)     extends Token
  case class NUMBER(value: BigDecimal) extends Token
  case class IDENT(value: String)      extends Token
  case object SPLICE                   extends Token
  case object EOF                      extends Token
  case object OBJECT_START             extends Literal("{")
  case object OBJECT_END               extends Literal("}")
  case object ARRAY_START              extends Literal("[")
  case object ARRAY_END                extends Literal("]")
  case object REPEAT                   extends Literal("*")
  case object COLON                    extends Literal(":")
  case object COMMA                    extends Literal(",")
  case object TRUE                     extends Literal("true")
  case object FALSE                    extends Literal("false")
  case object NULL                     extends Literal("null")
}

object Lex {
  import Token._
  import Util._

  def apply(s: String): Iterator[Token] = lex(s) ++ Iterator(EOF)

  def apply(parts: Seq[String]): Iterator[Token] = {
    (for {
      (part, i) <- parts.iterator.zipWithIndex
      splice = if (i == 0) Iterator() else Iterator(SPLICE)
      token <- splice ++ lex(part)
    } yield token) ++ Iterator(EOF)
  }

  def lex(s: String): Iterator[Token] = lex(s.iterator.buffered)

  def lex(implicit it: BufferedIterator[Char]): Iterator[Token] = new Iterator[Token] {
    def hasNext: Boolean = {
      skipWhitespace
      it.hasNext
    }

    def next(): Token = {
      skipWhitespace
      it.head match {
        case '{' => lexLiteral(OBJECT_START)
        case '}' => lexLiteral(OBJECT_END)
        case '[' => lexLiteral(ARRAY_START)
        case ']' => lexLiteral(ARRAY_END)
        case '*' => lexLiteral(REPEAT)
        case ':' => lexLiteral(COLON)
        case ',' => lexLiteral(COMMA)
        case '"' => lexString
        case c if c.isDigit  || c == '-' => lexNumber
        case c if c.isLetter || c == '_' => lexToken
      }
    }
  }

  def lexLiteral(lit: Literal)(implicit it: BufferedIterator[Char]): Literal = {
    for (c <- lit.value) expect[Char](c)
    lit
  }

  def lexToken(implicit it: BufferedIterator[Char]): Token = {
    val b = new StringBuilder
    b += accept(c => c.isLetter || c == '_')
    b ++= acceptRun(c => c.isLetterOrDigit || c == '_' || c == '-')
    b.toString match {
      case "true" => TRUE
      case "false" => FALSE
      case "null" => NULL
      case s => IDENT(s)
    }
  }

  def lexString(implicit it: BufferedIterator[Char]): Token = {
    val b = new StringBuilder
    expect[Char]('"')
    while (it.head != '"') b += lexChar
    expect[Char]('"')
    STRING(b.toString)
  }

  def lexChar(implicit it: BufferedIterator[Char]): Char = {
    it.head match {
      case '\\' =>
        it.next()
        it.head match {
          case '\\' => it.next()
          case '"' => it.next()
          case '/' => it.next()
          case 'b' => it.next(); '\b'
          case 'f' => it.next(); '\f'
          case 'n' => it.next(); '\n'
          case 'r' => it.next(); '\r'
          case 't' => it.next(); '\t'
          case 'u' =>
            it.next()
            val digits = for (_ <- 1 to 4) yield accept(HEX_DIGIT)
            Integer.parseInt(digits.mkString, 16).asInstanceOf[Char]
        }

      case _ =>
        it.next()
    }
  }

  val DIGIT = "0123456789"
  val HEX_DIGIT = "0123456789abcdefABCDEF"

  def lexNumber(implicit it: BufferedIterator[Char]): Token = {
    val b = new StringBuilder
    b ++= acceptOpt("-")
    b ++= lexInt
    acceptOpt(".").foreach { c =>
      b += c
      b ++= accept(DIGIT) + acceptRun(DIGIT)
    }
    acceptOpt("eE").foreach { c =>
      b += c
      b ++= acceptOpt("+-")
      b ++= accept(DIGIT) + acceptRun(DIGIT)
    }
    NUMBER(BigDecimal(b.toString))
  }

  def lexInt(implicit it: BufferedIterator[Char]): String = {
    it.head match {
      case '0' => it.next().toString
      case '1' | '2' | '3' | '4' | '5' | '6' | '7' | '8' | '9' =>
        it.next() + acceptRun(DIGIT)
    }
  }

  private def skipWhitespace(implicit it: BufferedIterator[Char]): Unit =
    while (it.hasNext && it.head.isWhitespace) it.next()

  private def accept(f: Char => Boolean)(implicit it: BufferedIterator[Char]): Char = {
    val c = it.next()
    require(f(c))
    c
  }

  private def acceptOpt(f: Char => Boolean)(implicit it: BufferedIterator[Char]): Option[Char] =
    if (it.hasNext && f(it.head)) Some(it.next()) else None

  private def acceptRun(f: Char => Boolean)(implicit it: BufferedIterator[Char]): String = {
    val b = new StringBuilder
    var done = false
    while (!done) {
      acceptOpt(f) match {
        case Some(c) => b += c
        case None => done = true
      }
    }
    b.toString
  }

  private def accept(s: String)(implicit it: BufferedIterator[Char]): Char = accept(s contains _)
  private def acceptOpt(s: String)(implicit it: BufferedIterator[Char]): Option[Char] = acceptOpt(s contains _)
  private def acceptRun(s: String)(implicit it: BufferedIterator[Char]): String = acceptRun(s contains _)
}
