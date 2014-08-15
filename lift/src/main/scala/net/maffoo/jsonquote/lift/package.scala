package net.maffoo.jsonquote

import net.liftweb.json._
import scala.language.experimental.macros
import scala.reflect.macros.Context

package object lift {
  /**
   * Rendering JValue produces valid json literal
   */
  implicit class LiftToLiteralJson(val json: JValue) extends AnyVal {
    def toLiteral: literal.Json = new literal.Json(compact(render(json)))
  }

  implicit class RichJsonSringContext(val sc: StringContext) extends AnyVal {
    def json(args: Any*): JValue = macro jsonImpl
  }

  def jsonImpl(c: Context)(args: c.Expr[Any]*): c.Expr[JValue] = {
    import c.universe._

    // fully-qualified symbols and types (for hygiene)
    val bigInt = q"_root_.scala.math.BigInt"
    val list = q"_root_.scala.collection.immutable.List"
    val nil = q"_root_.scala.collection.immutable.Nil"
    val seq = q"_root_.scala.Seq"
    val jArray = q"_root_.net.liftweb.json.JArray"
    val jField = q"_root_.net.liftweb.json.JField"
    val jObject = q"_root_.net.liftweb.json.JObject"
    val writesT = tq"_root_.net.maffoo.jsonquote.lift.Writes"

    // convert the given json AST to a tree with arguments spliced in at the correct locations
    def splice(js: JValue)(implicit args: Iterator[Tree]): Tree = js match {
      case SpliceValue()  => spliceValue(args.next)
      case SpliceValues() => c.abort(c.enclosingPosition, "cannot splice values at top level")

      case JObject(members) =>
        val ms = members.map {
          case SpliceField()      => q"$seq(${spliceField(args.next)})"
          case SpliceFields()     => spliceFields(args.next)
          case SpliceFieldNameOpt() => spliceFieldNameOpt(args.next, args.next)
          case SpliceFieldName(v) => q"$seq($jField(${spliceFieldName(args.next)}, ${splice(v)}))"
          case SpliceFieldOpt(k)  => spliceFieldOpt(k, args.next)
          case JField(k, v)       => q"$seq($jField($k, ${splice(v)}))"
        }
        q"$jObject($list(..$ms).flatten)"

      case JArray(elements) =>
        val es = elements.map {
          case SpliceValue()  => q"$seq(${spliceValue(args.next)})"
          case SpliceValues() => spliceValues(args.next)
          case e              => q"$seq(${splice(e)})"
        }
        q"$jArray($list(..$es).flatten)"

      case JString(s) => q"_root_.net.liftweb.json.JString($s)"
      case JDouble(n) => q"_root_.net.liftweb.json.JDouble($n)"
      case JInt(n)    => q"_root_.net.liftweb.json.JInt($bigInt(${n.toString}))"
      case JBool(b)   => q"_root_.net.liftweb.json.JBool($b)"
      case JNull      => q"_root_.net.liftweb.json.JNull"
    }

    def spliceValue(e: Tree): Tree = e.tpe match {
      case t if t <:< c.typeOf[JValue] => e
      case t =>
        inferWriter(e, t)
        q"implicitly[$writesT[$t]].write($e)"
    }

    def spliceValues(e: Tree): Tree = e.tpe match {
      case t if t <:< c.typeOf[Iterable[JValue]] => e
      case t if t <:< c.typeOf[Iterable[Any]] =>
        val valueTpe = typeParams(lub(t :: c.typeOf[Iterable[Nothing]] :: Nil))(0)
        val writer = inferWriter(e, valueTpe)
        q"$e.map($writer.write)"

      case t if t <:< c.typeOf[None.type] => nil
      case t if t <:< c.typeOf[Option[JValue]] => e
      case t if t <:< c.typeOf[Option[Any]] =>
        val valueTpe = typeParams(lub(t :: c.typeOf[Option[Nothing]] :: Nil))(0)
        val writer = inferWriter(e, valueTpe)
        q"$e.toIterable.map($writer.write)"

      case t => c.abort(e.pos, s"required Iterable[_] but got $t")
    }

    def spliceField(e: Tree): Tree = e.tpe match {
      case t if t <:< c.typeOf[JField] => e
      case t if t <:< c.typeOf[(String, JValue)] =>
        q"val (k, v) = $e; $jField(k, v)"
      case t if t <:< c.typeOf[(String, Any)] =>
        val valueTpe = typeParams(lub(t :: c.typeOf[(String, Nothing)] :: Nil))(1)
        val writer = inferWriter(e, valueTpe)
        q"val (k, v) = $e; $jField(k, $writer.write(v))"

      case t => c.abort(e.pos, s"required Iterable[(String, _)] but got $t")
    }

    def spliceFields(e: Tree): Tree = e.tpe match {
      case t if t <:< c.typeOf[Iterable[JField]] => e
      case t if t <:< c.typeOf[Iterable[(String, JValue)]] =>
        q"$e.map { case (k, v) => $jField(k, v) }"
      case t if t <:< c.typeOf[Iterable[(String, Any)]] =>
        val valueTpe = typeParams(lub(t :: c.typeOf[Iterable[(String, Nothing)]] :: Nil))(2)
        val writer = inferWriter(e, valueTpe)
        q"$e.map { case (k, v) => $jField(k, $writer.write(v)) }"

      case t if t <:< c.typeOf[None.type] => nil
      case t if t <:< c.typeOf[Option[JField]] =>
        q"$e.toIterable"
      case t if t <:< c.typeOf[Option[(String, JValue)]] =>
        q"$e.toIterable.map { case (k, v) => $jField(k, v) }"
      case t if t <:< c.typeOf[Option[(String, Any)]] =>
        val valueTpe = typeParams(lub(t :: c.typeOf[Option[(String, Nothing)]] :: Nil))(2)
        val writer = inferWriter(e, valueTpe)
        q"$e.toIterable.map { case (k, v) => $jField(k, $writer.write(v)) }"

      case t => c.abort(e.pos, s"required Iterable[(String, _)] but got $t")
    }

    def spliceFieldOpt(k: String, e: Tree): Tree = e.tpe match {
      case t if t <:< c.typeOf[None.type] => nil
      case t if t <:< c.typeOf[Option[JValue]] => q"$e.toIterable.map(v => $jField($k, v))"
      case t if t <:< c.typeOf[Option[Any]] =>
        val valueTpe = typeParams(lub(t :: c.typeOf[Option[Nothing]] :: Nil))(0)
        val writer = inferWriter(e, valueTpe)
        q"$e.toIterable.map { v => $jField($k, $writer.write(v)) }"

      case t => c.abort(e.pos, s"required Option[_] but got $t")
    }

    def spliceFieldName(e: Tree): Tree = e.tpe match {
      case t if t =:= c.typeOf[String] => e
      case t => c.abort(e.pos, s"required String but got $t")
    }

    def spliceFieldNameOpt(k: Tree, v: Tree): Tree = {
      k.tpe match {
        case t if t =:= c.typeOf[String] =>
        case t => c.abort(k.pos, s"required String but got $t")
      }
      v.tpe match {
        case t if t <:< c.typeOf[None.type] => nil
        case t if t <:< c.typeOf[Option[JValue]] => q"$v.toIterable.map(v => $jField($k, v))"
        case t if t <:< c.typeOf[Option[Any]] =>
          val valueTpe = typeParams(lub(t :: c.typeOf[Option[Nothing]] :: Nil))(0)
          val writer = inferWriter(v, valueTpe)
          q"$v.toIterable.map { v => $jField($k, $writer.write(v)) }"

        case t => c.abort(v.pos, s"required Option[_] but got $t")
      }
    }

    // return a list of type parameters in the given type
    // example: List[(String, Int)] => Seq(Tuple2, String, Int)
    def typeParams(tpe: Type): Seq[Type] = {
      val b = Iterable.newBuilder[Type]
      tpe.foreach(b += _)
      b.result.drop(2).grouped(2).map(_.head).toIndexedSeq
    }

    // locate an implicit Writes[T] for the given type
    def inferWriter(e: Tree, t: Type): Tree = {
      val writerTpe = appliedType(c.typeOf[Writes[_]], List(t))
      c.inferImplicitValue(writerTpe) match {
        case EmptyTree => c.abort(e.pos, s"could not find implicit value of type Writes[$t]")
        case tree => tree
      }
    }

    // Parse the string context parts into a json AST with holes, and then
    // typecheck/convert args to the appropriate types and splice them in.
    c.prefix.tree match {
      case Apply(_, List(Apply(_, partTrees))) =>
        val parts = partTrees map { case Literal(Constant(const: String)) => const }
        val positions = (parts zip partTrees.map(_.pos)).toMap
        val js = try {
          Parse(parts)
        } catch {
          case JsonError(msg, Pos(s, ofs)) =>
            val pos = positions(s)
            c.abort(pos.withPoint(pos.point + ofs), msg)
        }
        c.Expr[JValue](splice(js)(args.iterator.map(_.tree)))

      case _ =>
        c.abort(c.enclosingPosition, "invalid")
    }
  }
}
