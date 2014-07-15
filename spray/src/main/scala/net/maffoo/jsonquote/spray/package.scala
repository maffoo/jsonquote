package net.maffoo.jsonquote

import _root_.spray.json._
import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context

package object spray {
  implicit class RichJsonSringContext(val sc: StringContext) extends AnyVal {
    def json(args: Any*): JsValue = macro jsonImpl
  }

  def jsonImpl(c: Context)(args: c.Expr[Any]*): c.Expr[JsValue] = {
    import c.universe._

    // convert the given json AST to a tree with arguments spliced in at the correct locations
    def splice(js: JsValue)(implicit args: Iterator[Tree]): Tree = js match {
      case SpliceValue()  => spliceValue(args.next)
      case SpliceValues() => c.abort(c.enclosingPosition, "cannot splice values at top level")

      case JsObject(members) =>
        val ms = members.toSeq.map {
          case SpliceField()      => q"Seq(${spliceField(args.next)})"
          case SpliceFields()     => spliceFields(args.next)
          case SpliceFieldNameOpt() => spliceFieldNameOpt(args.next, args.next)
          case SpliceFieldName(v) => q"Seq((${spliceFieldName(args.next)}, ${splice(v)}))"
          case SpliceFieldOpt(k)  => spliceFieldOpt(k, args.next)
          case (k, v)             => q"Seq(($k, ${splice(v)}))"
        }
        q"JsObject(IndexedSeq(..$ms).flatten: _*)"

      case JsArray(elements) =>
        val es = elements.map {
          case SpliceValue()  => q"Seq(${spliceValue(args.next)})"
          case SpliceValues() => spliceValues(args.next)
          case e              => q"Seq(${splice(e)})"
        }
        q"JsArray(IndexedSeq(..$es).flatten: _*)"

      case JsString(s)      => q"JsString($s)"
      case JsNumber(n)      => q"JsNumber(BigDecimal(${n.toString}))"
      case JsBoolean(true)  => q"JsBoolean(true)"
      case JsBoolean(false) => q"JsBoolean(false)"
      case JsNull           => q"JsNull"
    }

    def spliceValue(e: Tree): Tree = e.tpe match {
      case t if t <:< c.typeOf[JsValue] => e
      case t =>
        inferWriter(e, t)
        q"implicitly[JsonWriter[$t]].write($e)"
    }

    def spliceValues(e: Tree): Tree = e.tpe match {
      case t if t <:< c.typeOf[Iterable[JsValue]] => e
      case t if t <:< c.typeOf[Iterable[Any]] =>
        val valueTpe = typeParams(lub(t :: c.typeOf[Iterable[Nothing]] :: Nil))(0)
        val writer = inferWriter(e, valueTpe)
        q"$e.map($writer.write)"

      case t if t <:< c.typeOf[None.type] => q"Nil"
      case t if t <:< c.typeOf[Option[JsValue]] => e
      case t if t <:< c.typeOf[Option[Any]] =>
        val valueTpe = typeParams(lub(t :: c.typeOf[Option[Nothing]] :: Nil))(0)
        val writer = inferWriter(e, valueTpe)
        q"$e.toIterable.map($writer.write)"

      case t => c.abort(e.pos, s"required Iterable[_] but got $t")
    }

    def spliceField(e: Tree): Tree = e.tpe match {
      case t if t <:< c.typeOf[(String, JsValue)] => e
      case t if t <:< c.typeOf[(String, Any)] =>
        val valueTpe = typeParams(lub(t :: c.typeOf[(String, Nothing)] :: Nil))(1)
        val writer = inferWriter(e, valueTpe)
        q"val (k, v) = $e; (k, $writer.write(v))"

      case t => c.abort(e.pos, s"required (String, _) but got $t")
    }

    def spliceFields(e: Tree): Tree = e.tpe match {
      case t if t <:< c.typeOf[Iterable[(String, JsValue)]] => e
      case t if t <:< c.typeOf[Iterable[(String, Any)]] =>
        val valueTpe = typeParams(lub(t :: c.typeOf[Iterable[(String, Nothing)]] :: Nil))(2)
        val writer = inferWriter(e, valueTpe)
        q"$e.map { case (k, v) => (k, $writer.write(v)) }"

      case t if t <:< c.typeOf[None.type] => q"Nil"
      case t if t <:< c.typeOf[Option[(String, JsValue)]] => e
      case t if t <:< c.typeOf[Option[(String, Any)]] =>
        val valueTpe = typeParams(lub(t :: c.typeOf[Option[(String, Nothing)]] :: Nil))(2)
        val writer = inferWriter(e, valueTpe)
        q"$e.toIterable.map { case (k, v) => (k, $writer.write(v)) }"

      case t => c.abort(e.pos, s"required Iterable[(String, _)] but got $t")
    }

    def spliceFieldOpt(k: String, e: Tree): Tree = e.tpe match {
      case t if t <:< c.typeOf[None.type] => q"Nil"
      case t if t <:< c.typeOf[Option[JsValue]] => q"$e.toIterable.map(v => ($k, v))"
      case t if t <:< c.typeOf[Option[Any]] =>
        val valueTpe = typeParams(lub(t :: c.typeOf[Option[Nothing]] :: Nil))(0)
        val writer = inferWriter(e, valueTpe)
        q"$e.toIterable.map { v => ($k, $writer.write(v)) }"

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
        case t if t <:< c.typeOf[None.type] => q"Nil"
        case t if t <:< c.typeOf[Option[JsValue]] => q"$v.toIterable.map(v => ($k, v))"
        case t if t <:< c.typeOf[Option[Any]] =>
          val valueTpe = typeParams(lub(t :: c.typeOf[Option[Nothing]] :: Nil))(0)
          val writer = inferWriter(v, valueTpe)
          q"$v.toIterable.map { v => ($k, $writer.write(v)) }"

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
      val writerTpe = appliedType(c.typeOf[JsonWriter[_]], List(t))
      c.inferImplicitValue(writerTpe) match {
        case EmptyTree => c.abort(e.pos, s"could not find implicit value of type JsonWriter[$t]")
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
        c.Expr[JsValue](splice(js)(args.iterator.map(_.tree)))

      case _ =>
        c.abort(c.enclosingPosition, "invalid")
    }
  }
}
