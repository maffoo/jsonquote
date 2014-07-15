package net.maffoo.jsonquote.literal

import net.maffoo.jsonquote._
import net.maffoo.jsonquote.Token._
import net.maffoo.jsonquote.Util._
import net.maffoo.jsonquote.literal.Json.quoteString
import scala.reflect.macros.Context

sealed trait Segment
case class Chunk(s: String) extends Segment
case object SpliceValue extends Segment
case object SpliceValues extends Segment
case object SpliceField extends Segment
case object SpliceFields extends Segment
case object SpliceFieldName extends Segment
case class SpliceFieldOpt(k: String) extends Segment

object Parse {
  /**
   * Helper method used when splicing literal json strings.
   *
   * This avoids the double-comma which would otherwise occur
   * when we splice an empty Iterable or Option. For example:
   *
   * val xs = Nil
   * json"[ 1, ..$xs, 2 ]"
   *
   * Naively using the empty string for xs would give [1,,2].
   * Hence, if while coalescing we encounter an empty string
   * and the next character is a comma, we drop the comma.
   */
  def coalesce(segments: String*): Json = {
    val it = segments.iterator
    val b = new StringBuilder
    while (it.hasNext) {
      val s = it.next
      b.append(if (s.startsWith(",") && b.last == ',') s.drop(1) else s)
    }
    new Json(b.toString)
  }

  def apply(s: Seq[String]): Seq[Segment] = apply(Lex(s))

  def apply(it: Iterator[(Token, Pos)]): Seq[Segment] = {
    val segments = parseValue(it.buffered).buffered
    expect[Token](EOF)(it)
    val out = IndexedSeq.newBuilder[Segment]
    while (segments.hasNext) {
      segments.head match {
        case _: Chunk =>
          val chunk = new StringBuilder
          while (segments.hasNext && segments.head.isInstanceOf[Chunk]) {
            chunk ++= segments.next.asInstanceOf[Chunk].s
          }
          out += Chunk(chunk.toString)
        case _ =>
          out += segments.next
      }
    }
    out.result
  }

  def parseValue(implicit it: BufferedIterator[(Token, Pos)]): Iterator[Segment] = {
    val (tok, pos) = it.head
    tok match {
      case OBJECT_START => parseObject
      case ARRAY_START  => parseArray
      case NUMBER(n)    => it.next(); Iterator(Chunk(n.toString))
      case STRING(s)    => it.next(); Iterator(Chunk('"' + quoteString(s) + '"'))
      case TRUE         => it.next(); Iterator(Chunk("true"))
      case FALSE        => it.next(); Iterator(Chunk("false"))
      case NULL         => it.next(); Iterator(Chunk("null"))
      case SPLICE       => it.next(); Iterator(SpliceValue)
      case tok          => throw JsonError(s"unexpected token: $tok", pos)
    }
  }

  def parseArray(implicit it: BufferedIterator[(Token, Pos)]): Iterator[Segment] = {
    expect[Token](ARRAY_START)
    val elems = if (it.head._1 == ARRAY_END) Iterator.empty else parseElements
    expect[Token](ARRAY_END)
    Iterator(Chunk("[")) ++ elems ++ Iterator(Chunk("]"))
  }

  def parseElements(implicit it: BufferedIterator[(Token, Pos)]): Iterator[Segment] = {
    val b = Seq.newBuilder[Segment]
    def advance(first: Boolean): Iterator[Segment] = {
      if (it.head._1 == REPEAT) {
        it.next()
        expect[Token](SPLICE)
        Iterator(SpliceValues)
      } else {
        parseValue
      }
    }
    b ++= advance(first = true)
    while (it.head._1 != ARRAY_END) {
      expect[Token](COMMA)
      b += Chunk(",")
      b ++= advance(first = false)
    }
    b.result.iterator
  }

  def parseObject(implicit it: BufferedIterator[(Token, Pos)]): Iterator[Segment] = {
    expect[Token](OBJECT_START)
    val members = if (it.head._1 == OBJECT_END) Iterator.empty else parseMembers
    expect[Token](OBJECT_END)
    Iterator(Chunk("{")) ++ members ++ Iterator(Chunk("}"))
  }

  def parseMembers(implicit it: BufferedIterator[(Token, Pos)]): Iterator[Segment] = {
    val b = Seq.newBuilder[Segment]
    def advance(first: Boolean): Iterator[Segment] = {
      if (it.head._1 == REPEAT) {
        it.next()
        expect[Token](SPLICE)
        Iterator(SpliceFields)
      } else {
        parsePair(first)
      }
    }
    b ++= advance(first = true)
    while (it.head._1 != OBJECT_END) {
      expect[Token](COMMA)
      b += Chunk(",")
      b ++= advance(first = false)
    }
    b.result.iterator
  }

  def parsePair(first: Boolean)(implicit it: BufferedIterator[(Token, Pos)]): Iterator[Segment] = {
    val (tok, pos) = it.next()
    tok match {
      case STRING(k) =>
        expect[Token](COLON)
        it.head._1 match {
          case OPTIONAL =>
            it.next()
            expect[Token](SPLICE)
            Iterator(SpliceFieldOpt(k))

          case _ => Iterator(Chunk('"' + quoteString(k) + '"'), Chunk(":")) ++ parseValue
        }

      case IDENT(k) =>
        expect[Token](COLON)
        it.head._1 match {
          case OPTIONAL =>
            it.next()
            expect[Token](SPLICE)
            Iterator(SpliceFieldOpt(k))

          case _ => Iterator(Chunk('"' + quoteString(k) + '"'), Chunk(":")) ++ parseValue
        }

      case SPLICE =>
        it.head._1 match {
          case COLON =>
            it.next()
            Iterator(SpliceFieldName, Chunk(":")) ++ parseValue

          case _ =>
            Iterator(SpliceField)
        }

      case tok =>
        throw JsonError(s"expected field but got $tok", pos)
    }
  }
}
