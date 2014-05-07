package net.maffoo.jsonquote

import scala.language.experimental.macros
import scala.reflect.macros.Context
import scala.util.parsing.json.JSONFormat.quoteString

package object literal {
  implicit class RichJsonStringContext(val sc: StringContext) extends AnyVal {
    def json(args: Any*): Json = macro jsonImpl
  }

  def jsonImpl(c: Context)(args: c.Expr[Any]*): c.Expr[Json] = {
    import c.universe._

    // convert the given json segment to a string, escaping spliced values as needed
    def splice(segment: Segment)(implicit args: Iterator[Tree]): Tree = segment match {
      case SpliceValue  => spliceValue(args.next)
      case SpliceValues => spliceValues(args.next)

      case SpliceField  => spliceField(args.next)
      case SpliceFields => spliceFields(args.next)
      case SpliceFieldName => spliceFieldName(args.next)
      case SpliceFieldOpt(k) => spliceFieldOpt(k, args.next)

      case Chunk(s) => Literal(Constant(s))
    }

    def spliceValue(e: Tree): Tree = e.tpe match {
      case t if t <:< c.typeOf[Json] => q"$e.toString"
      case t => q"implicitly[Writes[$t]].write($e).toString"
    }

    def spliceValues(e: Tree): Tree = e.tpe match {
      case t if t <:< c.typeOf[Nil.type] => Literal(Constant(""))
      case t if t <:< c.typeOf[Iterable[Json]] =>
        q"""$e.mkString(",")"""
      case t if t <:< c.typeOf[Iterable[Any]] =>
        val valueTpe = typeParams(lub(t :: c.typeOf[Iterable[Nothing]] :: Nil))(0)
        val writer = inferWriter(e, valueTpe)
        q"""$e.map($writer.write).mkString(",")"""

      case t if t <:< c.typeOf[None.type] => Literal(Constant(""))
      case t if t <:< c.typeOf[Option[Json]] =>
        q"""$e.map(_.toString).getOrElse("")"""
      case t if t <:< c.typeOf[Option[Any]] =>
        val valueTpe = typeParams(lub(t :: c.typeOf[Option[Nothing]] :: Nil))(0)
        val writer = inferWriter(e, valueTpe)
        q"""$e.map($writer.write).map(_.toString).getOrElse("")"""

      case t => c.abort(e.pos, s"required Iterable[_] but got $t")
    }

    def spliceField(e: Tree): Tree = e.tpe match {
      case t if t <:< c.typeOf[(String, Json)] =>
        q"""val (k, v) = $e; implicitly[Writes[String]].write(k) + ":" + v"""
      case t if t <:< c.typeOf[(String, Any)] =>
        val valueTpe = typeParams(lub(t :: c.typeOf[(String, Nothing)] :: Nil))(1)
        val writer = inferWriter(e, valueTpe)
        q"""val (k, v) = $e; implicitly[Writes[String]].write(k) + ":" + $writer.write(v)"""

      case t => c.abort(e.pos, s"required Iterable[(String, _)] but got $t")
    }

    def spliceFields(e: Tree): Tree = e.tpe match {
      case t if t <:< c.typeOf[Nil.type] => Literal(Constant(""))
      case t if t <:< c.typeOf[Iterable[(String, Json)]] =>
        q"""$e.map { case (k, v) => implicitly[Writes[String]].write(k) + ":" + v }.mkString(",")"""
      case t if t <:< c.typeOf[Iterable[(String, Any)]] =>
        val valueTpe = typeParams(lub(t :: c.typeOf[Iterable[(String, Nothing)]] :: Nil))(2)
        val writer = inferWriter(e, valueTpe)
        q"""$e.map { case (k, v) => implicitly[Writes[String]].write(k) + ":" + $writer.write(v) }.mkString(",")"""

      case t if t <:< c.typeOf[None.type] => Literal(Constant(""))
      case t if t <:< c.typeOf[Option[(String, Json)]] =>
        q"""$e.map { case (k, v) => implicitly[Writes[String]].write(k) + ":" + v }.getOrElse("")"""
      case t if t <:< c.typeOf[Option[(String, Any)]] =>
        val valueTpe = typeParams(lub(t :: c.typeOf[Option[(String, Nothing)]] :: Nil))(2)
        val writer = inferWriter(e, valueTpe)
        q"""$e.map { case (k, v) => implicitly[Writes[String]].write(k) + ":" + $writer.write(v) }.getOrElse("")"""

      case t => c.abort(e.pos, s"required Iterable[(String, _)] but got $t")
    }

    def spliceFieldOpt(k: String, e: Tree): Tree = e.tpe match {
      case t if t <:< c.typeOf[None.type] => Literal(Constant(""))
      case t if t <:< c.typeOf[Option[Json]] =>
        q"""$e.map { v => implicitly[Writes[String]].write($k) + ":" + v }.getOrElse("")"""
      case t if t <:< c.typeOf[Option[Any]] =>
        val valueTpe = typeParams(lub(t :: c.typeOf[Option[Nothing]] :: Nil))(0)
        val writer = inferWriter(e, valueTpe)
        q"""$e.map { v => implicitly[Writes[String]].write($k) + ":" + $writer.write(v) }.getOrElse("")"""

      case t => c.abort(e.pos, s"required Option[_] but got $t")
    }

    def spliceFieldName(e: Tree): Tree = e.tpe match {
      case t if t =:= c.typeOf[String] => q"""implicitly[Writes[String]].write($e).toString"""
      case t => c.abort(e.pos, s"required String but got $t")
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
        val segments = Parse(parts)
        val argsIter = args.iterator.map(_.tree)
        val trees = segments.map(s => splice(s)(argsIter))
        c.Expr[Json](q"Parse.coalesce(..$trees)")

      case _ =>
        c.abort(c.enclosingPosition, "invalid")
    }
  }
}