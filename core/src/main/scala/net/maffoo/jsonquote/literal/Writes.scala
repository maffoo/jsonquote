package net.maffoo.jsonquote.literal

import scala.util.parsing.json.JSONFormat.quoteString

// Typeclasses for converting to literal Json.
// Borrowed from the Writes mechanism in play-json.

trait Writes[A] {
  def write(a: A): Json
}

// Converters for basic scala types.
// TODO: expand this
object Writes {
  implicit object JsonWrites extends Writes[Json] {
    def write(a: Json): Json = a
  }

  implicit object IntWrites extends Writes[Int] {
    def write(n: Int): Json = Json(n.toString)
  }

  implicit object DoubeWrites extends Writes[Double] {
    def write(n: Double): Json = Json(n.toString)
  }

  implicit object StringWrites extends Writes[String] {
    def write(s: String): Json = Json('"' + quoteString(s) + '"')
  }

  implicit def optionWrites[A: Writes]: Writes[Option[A]] = new Writes[Option[A]] {
    def write(o: Option[A]): Json = o match {
      case Some(a) => implicitly[Writes[A]].write(a)
      case None => Json("null")
    }
  }

  implicit def seqWrites[A: Writes]: Writes[Seq[A]] = new Writes[Seq[A]] {
    def write(s: Seq[A]): Json = {
      val writer = implicitly[Writes[A]]
      Json(s.map(writer.write).mkString("[", ",", "]"))
    }
  }
}
