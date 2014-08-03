package net.maffoo.jsonquote.lift

import net.liftweb.json._

// Typeclasses for converting to lift JValues.
// Borrowed from the Writes mechanism in play-json.

trait Writes[A] {
  def write(a: A): JValue
}

// Converters for basic scala types.
object Writes {
  implicit object JValueWrites extends Writes[JValue] {
    def write(a: JValue): JValue = a
  }

  implicit object BoolWrites extends Writes[Boolean] {
    def write(b: Boolean): JValue = JBool(b)
  }

  implicit object ByteWrites extends Writes[Byte] {
    def write(n: Byte): JValue = JInt(n)
  }

  implicit object ShortWrites extends Writes[Short] {
    def write(n: Short): JValue = JInt(n)
  }

  implicit object IntWrites extends Writes[Int] {
    def write(n: Int): JValue = JInt(n)
  }

  implicit object LongWrites extends Writes[Long] {
    def write(n: Long): JValue = JInt(n)
  }

  implicit object DoubleWrites extends Writes[Double] {
    def write(n: Double): JValue = JDouble(n)
  }

  implicit object StringWrites extends Writes[String] {
    def write(s: String): JValue = JString(s)
  }

//  implicit def optionWrites[A: Writes]: Writes[Option[A]] = new Writes[Option[A]] {
//    def write(o: Option[A]): JValue = o match {
//      case Some(a) => implicitly[Writes[A]].write(a)
//      case None => JNull
//    }
//  }

  implicit def seqWrites[A: Writes]: Writes[Seq[A]] = new Writes[Seq[A]] {
    def write(s: Seq[A]): JValue = {
      val writer = implicitly[Writes[A]]
      JArray(s.map(writer.write).toList)
    }
  }

  implicit def mapWrites[A: Writes]: Writes[Map[String, A]] = new Writes[Map[String, A]] {
    def write(m: Map[String, A]): JValue = {
      val writer = implicitly[Writes[A]]
      JObject(m.map { case (k, v) => JField(k, writer.write(v)) }.toList)
    }
  }
}
