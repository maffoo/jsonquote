package net.maffoo.jsonquote

import _root_.play.api.libs.json._
import scala.language.experimental.macros
import scala.reflect.macros.Context

package object play {
  implicit class RichJsonSringContext(val sc: StringContext) extends AnyVal {
    def json(args: Any*): JsValue = macro jsonImpl
  }

  def jsonImpl(c: Context)(args: c.Expr[Any]*): c.Expr[JsValue] = {
    import c.universe._

    def liftSeq[A: WeakTypeTag](exprs: Seq[c.Expr[A]]): c.Expr[Seq[A]] = {
      val trees = exprs.map(_.tree).toList
      c.Expr[Seq[A]](Apply(Select(Ident(newTermName("Seq")), newTermName("apply")), trees))
    }

    // convert a json AST known at compile time into an equivalent runtime expression
    def lift(js: JsValue): c.Expr[JsValue] = js match {
      case SpliceValue()  => reify { SpliceValue() }
      case SpliceValues() => sys.error("cannot inject values at top level")

      case JsObject(members) =>
        val ms = liftSeq(members.map {
          case SpliceField()      => reify { SpliceField() }
          case SpliceFields()     => reify { SpliceFields() }
          case SpliceFieldName(v) => reify { SpliceFieldName(lift(v).splice) }
          case (k, v)             => reify { (liftString(k).splice, lift(v).splice) }
        })
        reify { JsObject(ms.splice) }

      case JsArray(elements) =>
        val es = liftSeq(elements.map {
          case SpliceValue()  => reify { SpliceValue() }
          case SpliceValues() => reify { SpliceValues() }
          case e              => lift(e)
        })
        reify { JsArray(es.splice) }

      case JsString(s) => reify { JsString(liftString(s).splice) }
      case JsNumber(n) => reify { JsNumber(liftNumber(n).splice) }

      case JsBoolean(true)  => reify { JsBoolean(true) }
      case JsBoolean(false) => reify { JsBoolean(false) }
      case JsNull           => reify { JsNull }
    }

    def liftString(s: String): c.Expr[String] = c.Expr[String](Literal(Constant(s)))
    def liftNumber(n: BigDecimal): c.Expr[BigDecimal] = reify { BigDecimal(liftString(n.toString).splice) }

    // walk the given json AST and convert args to be spliced based on their locations in the tree
    def convertArgs(js: JsValue, args: Iterator[c.Expr[Any]]) = {
      val builder = Seq.newBuilder[c.Expr[Any]]
      def walk(js: JsValue): Unit = js match {
        case SpliceValue()  => builder += convertValue(args.next)
        case SpliceValues() => builder += convertValues(args.next)

        case JsObject(members) =>
          members.foreach {
            case SpliceField()      => builder += convertField(args.next)
            case SpliceFields()     => builder += convertFields(args.next)
            case SpliceFieldName(v) => builder += convertFieldName(args.next); walk(v)
            case (_, v)             => walk(v)
          }

        case JsArray(elements) =>
          elements.foreach(walk)

        case _ =>
      }
      walk(js)
      builder.result
    }

    def convertValue(e: c.Expr[Any]): c.Expr[Any] = e.tree.tpe match {
      case t if t <:< c.typeOf[JsValue] => e
      case t =>
        val writer = inferWriter(e, t)
        reify { writer.splice.writes(e.splice) }
    }

    def convertValues(e: c.Expr[Any]): c.Expr[Any] = e.tree.tpe match {
      case t if t <:< c.typeOf[Iterable[JsValue]] => e

      case t if t <:< c.typeOf[Iterable[Any]] =>
        val valueTpe = typeParams(lub(t :: c.typeOf[Iterable[Nothing]] :: Nil))(0)
        val writer = inferWriter(e, valueTpe)
        val it = c.Expr[Iterable[Any]](e.tree)
        reify { it.splice.map(writer.splice.writes) }

      case t if t <:< c.typeOf[Option[JsValue]] => e

      case t if t <:< c.typeOf[Option[Any]] =>
        val valueTpe = typeParams(lub(t :: c.typeOf[Option[Nothing]] :: Nil))(0)
        val writer = inferWriter(e, valueTpe)
        val it = c.Expr[Option[Any]](e.tree)
        reify { it.splice.map(writer.splice.writes) }

      case t => c.abort(e.tree.pos, s"required Iterable[_] but got $t")
    }

    def convertField(e: c.Expr[Any]): c.Expr[Any] = e.tree.tpe match {
      case t if t <:< c.typeOf[(String, JsValue)] => e

      case t if t <:< c.typeOf[(String, Any)] =>
        val valueTpe = typeParams(lub(t :: c.typeOf[(String, Nothing)] :: Nil))(1)
        val writer = inferWriter(e, valueTpe)
        val kv = c.Expr[(String, Any)](e.tree)
        reify { val (k, v) = kv.splice; (k, writer.splice.writes(v)) }

      case t => c.abort(e.tree.pos, s"required Iterable[(String, _)] but got $t")
    }

    def convertFields(e: c.Expr[Any]): c.Expr[Any] = e.tree.tpe match {
      case t if t <:< c.typeOf[Iterable[(String, JsValue)]] => e

      case t if t <:< c.typeOf[Iterable[(String, Any)]] =>
        val valueTpe = typeParams(lub(t :: c.typeOf[Iterable[(String, Nothing)]] :: Nil))(2)
        val writer = inferWriter(e, valueTpe)
        val it = c.Expr[Iterable[Any]](e.tree)
        reify { it.splice.map { case (k, v) => (k, writer.splice.writes(v)) } }

      case t if t <:< c.typeOf[Option[(String, JsValue)]] => e

      case t if t <:< c.typeOf[Option[(String, Any)]] =>
        val valueTpe = typeParams(lub(t :: c.typeOf[Option[(String, Nothing)]] :: Nil))(2)
        val writer = inferWriter(e, valueTpe)
        val it = c.Expr[Option[Any]](e.tree)
        reify { it.splice.map { case (k, v) => (k, writer.splice.writes(v)) } }

      case t =>
        c.abort(e.tree.pos, s"required Iterable[(String, _)] but got $t")
    }

    def convertFieldName(e: c.Expr[Any]): c.Expr[Any] = e.tree.tpe match {
      case t if t =:= c.typeOf[String] => e
      case t => c.abort(e.tree.pos, s"required String but got $t")
    }

    // return a list of type parameters in the given type
    // example: List[(String, Int)] => Seq(Tuple2, String, Int)
    def typeParams(tpe: Type): Seq[Type] = {
      val b = Iterable.newBuilder[Type]
      tpe.foreach(b += _)
      b.result.drop(2).grouped(2).map(_.head).toIndexedSeq
    }

    // locate an implicit Writes[T] for the given type
    def inferWriter(e: c.Expr[_], t: Type): c.Expr[Writes[Any]] = {
      val writerTpe = appliedType(c.typeOf[Writes[_]], List(t))
      c.inferImplicitValue(writerTpe) match {
        case EmptyTree => c.abort(e.tree.pos, s"could not find implicit value of type Writes[$t]")
        case tree => c.Expr[Writes[Any]](tree)
      }
    }

    // Parse the string context parts into a json AST with splice points
    // and typecheck/convert args to types appropriate for each splice point.
    // At runtime, the converted values will be spliced into the lifted AST.
    c.prefix.tree match {
      case Apply(_, List(Apply(_, partTrees))) =>
        val parts = partTrees map { case Literal(Constant(const: String)) => const }
        val js = Parse(parts)
        val convertedArgs = convertArgs(js, args.iterator)
        reify { Splice(lift(js).splice, liftSeq(convertedArgs).splice) }

      case _ =>
        c.abort(c.enclosingPosition, "invalid")
    }
  }
}
