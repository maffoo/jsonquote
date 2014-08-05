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
  case object REPEAT                   extends Literal("..")
  case object OPTIONAL                 extends Literal("?")
  case object COLON                    extends Literal(":")
  case object COMMA                    extends Literal(",")
  case object TRUE                     extends Literal("true")
  case object FALSE                    extends Literal("false")
  case object NULL                     extends Literal("null")
}

case class Pos(string: String, offset: Int)

object Lex {
  import Token._
  import Util._

  type State = BufferedIterator[(Char, Pos)]

  def apply(s: String): Iterator[(Token, Pos)] = lex(s) ++ Iterator((EOF, Pos(s, s.length)))

  def apply(parts: Seq[String]): Iterator[(Token, Pos)] = {
    (for {
      (part, i) <- parts.iterator.zipWithIndex
      splice = if (i == 0) Iterator() else Iterator((SPLICE, Pos(part, part.length)))
      (token, pos) <- splice ++ lex(part)
    } yield (token, pos)) ++ Iterator((EOF, Pos(parts.last, parts.last.length)))
  }

  def lex(s: String): Iterator[(Token, Pos)] =
    lex(s.iterator.zipWithIndex.map { case (c, i) => (c, Pos(s, i)) }.buffered)

  def lex(implicit it: State): Iterator[(Token, Pos)] = new Iterator[(Token, Pos)] {
    def hasNext: Boolean = {
      skipWhitespace
      it.hasNext
    }

    def next(): (Token, Pos) = {
      skipWhitespace
      val (c, pos) = it.head
      val token = c match {
        case '{' => lexLiteral(OBJECT_START)
        case '}' => lexLiteral(OBJECT_END)
        case '[' => lexLiteral(ARRAY_START)
        case ']' => lexLiteral(ARRAY_END)
        case '.' => lexLiteral(REPEAT)
        case '?' => lexLiteral(OPTIONAL)
        case ':' => lexLiteral(COLON)
        case ',' => lexLiteral(COMMA)
        case '"' => lexString
        case c if c.isDigit  || c == '-' => lexNumber
        case c if c.isLetter || c == '_' => lexToken
      }
      (token, pos)
    }
  }

  def lexLiteral(lit: Literal)(implicit it: State): Literal = {
    for (c <- lit.value) expect[Char](c)
    lit
  }

  def lexToken(implicit it: State): Token = {
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

  def lexString(implicit it: State): Token = {
    val b = new StringBuilder
    expect[Char]('"')
    while (it.head._1 != '"') b += lexChar
    expect[Char]('"')
    STRING(b.toString)
  }

  def lexChar(implicit it: State): Char = {
    it.head._1 match {
      case '\\' =>
        it.next()
        it.head._1 match {
          case '\\' => it.next()._1
          case '"' => it.next()._1
          case '/' => it.next()._1
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
        it.next()._1
    }
  }

  val DIGIT = "0123456789"
  val HEX_DIGIT = "0123456789abcdefABCDEF"

  def lexNumber(implicit it: State): Token = {
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

  def lexInt(implicit it: State): String = {
    it.head._1 match {
      case '0' => it.next()._1.toString
      case '1' | '2' | '3' | '4' | '5' | '6' | '7' | '8' | '9' =>
        it.next()._1 + acceptRun(DIGIT)
    }
  }

  @scala.annotation.tailrec
  private def skipWhitespace(implicit it: State): Unit = {
    while (it.hasNext && it.head._1.isWhitespace) it.next()
    if (it.hasNext && it.head._1 == '/') {
      skipComment
      skipWhitespace
    }
  }

  private def skipComment(implicit it: State): Unit = {
    expect('/')
    acceptOpt("/*") match {
      case Some('/') => skipLineComment
      case Some('*') => skipBlockComment
      case _ => sys.error("expected // or /* to start comment")
    }
  }

  private def skipLineComment(implicit it: State): Unit = {
    acceptRun(!"\r\n".contains(_))
    acceptRun("\r\n".contains(_))
  }

  @scala.annotation.tailrec
  private def skipBlockComment(implicit it: State): Unit = {
    acceptRun(_ != '*')
    expect('*')
    acceptOpt("/") match {
      case None => skipBlockComment
      case Some(_) => // done
    }
  }

  private def accept(f: Char => Boolean)(implicit it: State): Char = {
    val (c, _) = it.next()
    require(f(c))
    c
  }

  private def acceptOpt(f: Char => Boolean)(implicit it: State): Option[Char] =
    if (it.hasNext && f(it.head._1)) Some(it.next()._1) else None

  private def acceptRun(f: Char => Boolean)(implicit it: State): String = {
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

  private def accept(s: String)(implicit it: State): Char = accept(s contains _)
  private def acceptOpt(s: String)(implicit it: State): Option[Char] = acceptOpt(s contains _)
  private def acceptRun(s: String)(implicit it: State): String = acceptRun(s contains _)
}
