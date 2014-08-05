package net.maffoo.jsonquote.literal

import net.maffoo.jsonquote.literal.Json.quoteString

// Typeclasses for converting to literal Json.
// Borrowed from the Writes mechanism in play-json.

trait Writes[A] {
  def write(a: A): Json
}

// Converters for basic scala types.
object Writes {
  implicit object JsonWrites extends Writes[Json] {
    def write(a: Json): Json = a
  }

  implicit object BoolWrites extends Writes[Boolean] {
    def write(b: Boolean): Json = new Json(b.toString)
  }

  implicit object ByteWrites extends Writes[Byte] {
    def write(n: Byte): Json = new Json(n.toString)
  }

  implicit object ShortWrites extends Writes[Short] {
    def write(n: Short): Json = new Json(n.toString)
  }

  implicit object IntWrites extends Writes[Int] {
    def write(n: Int): Json = new Json(n.toString)
  }

  implicit object LongWrites extends Writes[Long] {
    def write(n: Long): Json = new Json(n.toString)
  }

  implicit object DoubleWrites extends Writes[Double] {
    def write(n: Double): Json = new Json(n.toString)
  }

  implicit object StringWrites extends Writes[String] {
    def write(s: String): Json = new Json('"' + quoteString(s) + '"')
  }

//  implicit def optionWrites[A: Writes]: Writes[Option[A]] = new Writes[Option[A]] {
//    def write(o: Option[A]): Json = o match {
//      case Some(a) => implicitly[Writes[A]].write(a)
//      case None => Json("null")
//    }
//  }

  implicit def seqWrites[A: Writes]: Writes[Seq[A]] = new Writes[Seq[A]] {
    def write(s: Seq[A]): Json = {
      val writer = implicitly[Writes[A]]
      new Json(s.map(writer.write).mkString("[", ",", "]"))
    }
  }

  implicit def mapWrites[A: Writes]: Writes[Map[String, A]] = new Writes[Map[String, A]] {
    def write(m: Map[String, A]): Json = {
      val keyWriter = StringWrites
      val valWriter = implicitly[Writes[A]]
      new Json(m.map { case (k, v) => keyWriter.write(k) + ":" + valWriter.write(v) }.mkString("{", ",", "}"))
    }
  }
}
