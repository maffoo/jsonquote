package net.maffoo.jsonquote.upickle

import org.scalatest.{FunSuite, Matchers}
import ujson.{Arr, Null, Num, Obj, Str, Value}
import upickle.default
import upickle.default.macroRW

case class Foo(bar: String, baz: String)

object OptionPickler extends upickle.AttributeTagged {
  override implicit def OptionWriter[T: Writer]: Writer[Option[T]] = {
    implicitly[Writer[T]].comap[Option[T]] {
      case None => null.asInstanceOf[T]
      case Some(x) => x
    }
  }

  override implicit def OptionReader[T: Reader]: Reader[Option[T]] = {
    new Reader.Delegate[Any, Option[T]](implicitly[Reader[T]].map(Some(_))){
      override def visitNull(index: Int) = None
    }
  }
}

class UpickleTest extends FunSuite with Matchers {
//  import _root_.upickle.default.{macroRW}

  import OptionPickler._
  import OptionPickler.macroRW

  implicit val FooRW: OptionPickler.ReadWriter[Foo] = macroRW


  implicit def Foo2Json(foo: Foo): Value = read[Value](write(foo))

  def check(js: Value, s: String): Unit = { js should equal (read[Value](s)) }

  //case class Baz(s: String)
  //json"""{test: ${Baz("test")}}"""

  test("can parse plain json") {
    check(json""" "hello!" """, """"hello!"""")
  }

  test("can use bare identifiers for object keys") {
    check(json"{ test0: 0 }", """{ "test0": 0 }""")
    check(json"{ test: 1 }", """{ "test": 1 }""")
    check(json"{ test-2: 2 }", """{ "test-2": 2 }""")
    check(json"{ test_3: 3 }", """{ "test_3": 3 }""")
    check(json"{ _test-4: 4 }", """{ "_test-4": 4 }""")
  }

  test("can inject value with implicit Writes") {
    val foo: Value = Foo(bar = "a", baz = "b")
    check(json"$foo", """{"bar": "a", "baz": "b"}""")
  }

  test("can inject values with implicit Writes") {
    val foos: List[Value] = List(Foo("1", "2"), Foo("3", "4"))
    check(json"[..$foos]", """[{"bar":"1", "baz":"2"}, {"bar":"3", "baz":"4"}]""")
    check(json"[..$Nil]", """[]""")
  }

  test("can inject Option values") {
    val vOpt = Some(Num(1))

    check(json"[..$vOpt]", """[1]""")
    check(json"[..$None]" , """[]""")
  }

  test("can inject Option values with implicit Writes") {
    val vOpt: Option[Value] = Some(1)
    check(json"[..$vOpt]", """[1]""")
  }

  test("can mix values, Iterables and Options in array") {
    val a: List[Value] = List("a")
    val b: Option[Value] = Some("b")
    val c: List[Value] = Nil
    val d: Option[Value] = None
    check(json"""[1, ..$a, 2, ..$b, 3, ..$c, 4, ..$d, 5]""", """[1, "a", 2, "b", 3, 4, 5]""")
  }

  test("can inject Tuple2 as object field") {
    val kv: (String, Value) = "test" -> Foo("1", "2")
    check(json"{$kv}", """{"test": {"bar":"1", "baz":"2"}}""")
  }

  //First one with error
  test("can inject multiple fields") {
    val kvs: Seq[(String, Value)] = Seq("a" -> 1, "b" -> 2)
    val kvs2: Seq[(String, Value)] = Nil
    check(json"{..$kvs}", """{"a": 1, "b": 2}""")
    check(json"{..$kvs2}", """{}""")
  }

  test("can inject just a field name") {
    val key = "foo"
    check(json"{$key: 1}", """{"foo": 1}""")
  }

  test("can inject Option fields") {
    val kvOpt: Option[(String, Num)] = Some("a" -> Num(1))
    val kvOpt2: Option[(String, Num)] = None
    check(json"{..$kvOpt}", """{"a": 1}""")
    check(json"{..$kvOpt2}", """{}""")
  }

  test("can inject Option fields with implicit Writes") {
    val kvOpt: Option[(String, Value)] = Some("a" -> Foo("1", "2"))
    check(json"{..$kvOpt}", """{"a": {"bar": "1", "baz": "2"}}""")
  }

  test("can inject Option field values") {
    val vOpt: Option[Num] = Some(Num(1))
    val vOpt2: Option[Num] = None
    check(json"{a:? $vOpt}", """{"a": 1}""")
    check(json"{a:? $vOpt2}", """{}""")

    val k = "a"
    check(json"{$k:? $vOpt}", """{"a": 1}""")
    check(json"{$k:? $vOpt2}", """{}""")
  }

  test("can inject Option field values with implicit Writes") {
    val vOpt: Option[Value] = Some(Foo("1", "2"))
    check(json"{a:? $vOpt}", """{"a": {"bar": "1", "baz": "2"}}""")
  }

  test("can mix values, Iterables and Options in object") {
    val a: List[(String, Value)] = List("a" -> 10)
    val b: Option[(String, Value)] = Some("b" -> 20)
    val c: List[(String, Value)] = Nil
    val d: Option[(String, Value)] = None
    check(
      json"""{i:1, ..$a, j:2, ..$b, k:3, ..$c, l:4, ..$d, m:5}""",
      """{"i":1, "a":10, "j":2, "b":20, "k":3, "l":4, "m":5}"""
    )
  }

  test("can nest jsonquote templates") {

    val jsonObject = Obj.from(
      Map(
        "users" -> Seq(
          Obj.from(
            Map(
              "name" -> Str("Bob"),
              "age" -> Num(31),
              "email" -> Str("bob@gmail.com")
            )
          ),
          Obj.from(
            Map(
              "name" -> Str("Kiki"),
              "age" -> Num(25),
              "email" -> Null
            )
          )
        )
      )
    )

    val users: Seq[(Value, Value, Option[Value])] = Seq(("Bob", 31, Some("bob@gmail.com")), ("Kiki", 25, None))

    // TODO: find a way to avoid the need for .toSeq here
    val quoteA = json"""{
      users: [..${
      users.map { case (name, age, email) =>
        json"""{
            name: $name,
            age: $age,
            email:? $email
          }"""
      }.toSeq
    }]
    }"""

    // play already knows how to convert Seq[JsValue] to json array
    // still need the .toSeq here
//    val quoteB = json"""{
//      users: ${
//      users.map { case (name, age, email) =>
//        json"""{
//            name: $name,
//            age: $age,
//            email:? $email
//          }"""
//      }.toSeq
//    }
//    }"""

    // types inferred properly here
    val mapped = users.map { case (name, age, email) =>
      json"""{
        name: $name,
        age: $age,
        email:? $email
      }"""
    }
    val quoteC = json"""{
      users: [..$mapped]
    }"""

    quoteA should equal (jsonObject)
//    quoteB should equal (jsonObject)
    quoteC should equal (jsonObject)
  }
}
