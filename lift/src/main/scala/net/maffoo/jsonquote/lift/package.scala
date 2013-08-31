package net.maffoo.jsonquote

import net.liftweb.json._
import scala.language.experimental.macros
import scala.reflect.macros.Context

package object lift {
  implicit class RichJsonSringContext(val sc: StringContext) extends AnyVal {
    def json(args: Any*): JValue = macro jsonImpl
  }

  def jsonImpl(c: Context)(args: c.Expr[Any]*): c.Expr[JValue] = {
    import c.universe._

    // convert the given json AST to a tree with arguments spliced in at the correct locations
    def splice(js: JValue)(implicit args: Iterator[Tree]): Tree = js match {
      case SpliceValue()  => spliceValue(args.next)
      case SpliceValues() => c.abort(c.enclosingPosition, "cannot splice values at top level")

      case JObject(members) =>
        val ms = members.map {
          case SpliceField()      => q"Seq(${spliceField(args.next)})"
          case SpliceFields()     => spliceFields(args.next)
          case SpliceFieldName(v) => q"Seq(JField(${spliceFieldName(args.next)}, ${splice(v)}))"
          case JField(k, v)       => q"Seq(JField($k, ${splice(v)}))"
        }
        q"JObject(List(..$ms).flatten)"

      case JArray(elements) =>
        val es = elements.map {
          case SpliceValue()  => q"Seq(${spliceValue(args.next)})"
          case SpliceValues() => spliceValues(args.next)
          case e              => q"Seq(${splice(e)})"
        }
        q"JArray(List(..$es).flatten)"

      case JString(s) => q"JString($s)"
      case JDouble(n) => q"JDouble($n)"
      case JInt(n)    => q"JInt(BigInt(${n.toString}))"
      case JBool(b)   => q"JBool($b)"
      case JNull      => q"JNull"
    }

    def spliceValue(e: Tree): Tree = e.tpe match {
      case t if t <:< c.typeOf[JValue] => e
      case t =>
        q"implicitly[Writes[$t]].write($e)"
    }

    def spliceValues(e: Tree): Tree = e.tpe match {
      case t if t <:< c.typeOf[Iterable[JValue]] => e
      case t if t <:< c.typeOf[Iterable[Any]] =>
        val valueTpe = typeParams(lub(t :: c.typeOf[Iterable[Nothing]] :: Nil))(0)
        val writer = inferWriter(e, valueTpe)
        q"$e.map($writer.write)"

      case t if t <:< c.typeOf[None.type] => q"Nil"
      case t if t <:< c.typeOf[Option[JValue]] => e
      case t if t <:< c.typeOf[Option[Any]] =>
        val valueTpe = typeParams(lub(t :: c.typeOf[Option[Nothing]] :: Nil))(0)
        val writer = inferWriter(e, valueTpe)
        q"Option.option2Iterable($e).map($writer.write)"

      case t => c.abort(e.pos, s"required Iterable[_] but got $t")
    }

    def spliceField(e: Tree): Tree = e.tpe match {
      case t if t <:< c.typeOf[JField] => e
      case t if t <:< c.typeOf[(String, JValue)] =>
        q"val (k, v) = $e; JField(k, v)"
      case t if t <:< c.typeOf[(String, Any)] =>
        val valueTpe = typeParams(lub(t :: c.typeOf[(String, Nothing)] :: Nil))(1)
        val writer = inferWriter(e, valueTpe)
        q"val (k, v) = $e; JField(k, $writer.write(v))"

      case t => c.abort(e.pos, s"required Iterable[(String, _)] but got $t")
    }

    def spliceFields(e: Tree): Tree = e.tpe match {
      case t if t <:< c.typeOf[Iterable[JField]] => e
      case t if t <:< c.typeOf[Iterable[(String, JValue)]] =>
        q"$e.map { case (k, v) => JField(k, v) }"
      case t if t <:< c.typeOf[Iterable[(String, Any)]] =>
        val valueTpe = typeParams(lub(t :: c.typeOf[Iterable[(String, Nothing)]] :: Nil))(2)
        val writer = inferWriter(e, valueTpe)
        q"$e.map { case (k, v) => JField(k, $writer.write(v)) }"

      case t if t <:< c.typeOf[None.type] => q"Nil"
      case t if t <:< c.typeOf[Option[JField]] =>
        q"Option.option2Iterable($e)"
      case t if t <:< c.typeOf[Option[(String, JValue)]] =>
        q"Option.option2Iterable($e).map { case (k, v) => JField(k, v) }"
      case t if t <:< c.typeOf[Option[(String, Any)]] =>
        val valueTpe = typeParams(lub(t :: c.typeOf[Option[(String, Nothing)]] :: Nil))(2)
        val writer = inferWriter(e, valueTpe)
        q"Option.option2Iterable($e).map { case (k, v) => JField(k, $writer.write(v)) }"

      case t => c.abort(e.pos, s"required Iterable[(String, _)] but got $t")
    }

    def spliceFieldName(e: Tree): Tree = e.tpe match {
      case t if t =:= c.typeOf[String] => e
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
        val js = Parse(parts)
        c.Expr[JValue](splice(js)(args.iterator.map(_.tree)))

      case _ =>
        c.abort(c.enclosingPosition, "invalid")
    }
  }
}
