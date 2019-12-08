package net.maffoo.jsonquote.upickle

import org.scalatest.{FunSuite, Matchers}
import ujson.{Arr, Null, Num, Obj, Str, Value}
import upickle.default
import upickle.default._
import upickle.default.macroRW

case class Foo(bar: String, baz: String)

class UpickleTest extends FunSuite with Matchers {

  implicit val FooRW: default.ReadWriter[Foo] = macroRW

  def check(js: Value, s: String): Unit = { js should equal (read[Value](s)) }

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
    val foo = Foo(bar = "a", baz = "b")
    check(json"$foo", """{"bar": "a", "baz": "b"}""")
  }

  test("can inject values with implicit Writes") {
    val foos = List(Foo("1", "2"), Foo("3", "4"))
    check(json"[..$foos]", """[{"bar":"1", "baz":"2"}, {"bar":"3", "baz":"4"}]""")
    check(json"[..$Nil]", """[]""")
  }

  test("can inject Option values") {
    val vOpt = Some(Num(1))

    check(json"[..$vOpt]", """[1]""")
    check(json"[..$None]" , """[]""")
  }

  test("can inject Option values with implicit Writes") {
    val vOpt = Some(1)
    check(json"[..$vOpt]", """[1]""")
  }

  test("can mix values, Iterables and Options in array") {
    val a = List("a")
    val b = Some("b")
    val c = Nil
    val d = None
    check(json"""[1, ..$a, 2, ..$b, 3, ..$c, 4, ..$d, 5]""", """[1, "a", 2, "b", 3, 4, 5]""")
  }

  test("can inject Tuple2 as object field") {
    val kv = "test" -> Foo("1", "2")
    check(json"{$kv}", """{"test": {"bar":"1", "baz":"2"}}""")
  }

  //First one with error
  test("can inject multiple fields") {
    val kvs = Seq("a" -> 1, "b" -> 2)
    val kvs2 = Nil
    check(json"{..$kvs}", """{"a": 1, "b": 2}""")
    check(json"{..$kvs2}", """{}""")
  }

  test("can inject just a field name") {
    val key = "foo"
    check(json"{$key: 1}", """{"foo": 1}""")
  }

  test("can inject Option fields") {
    val kvOpt = Some("a" -> 1)
    val kvOpt2 = None
    check(json"{..$kvOpt}", """{"a": 1}""")
    check(json"{..$kvOpt2}", """{}""")
  }

  test("can inject Option fields with implicit Writes") {
    val kvOpt = Some("a" -> Foo("1", "2"))
    check(json"{..$kvOpt}", """{"a": {"bar": "1", "baz": "2"}}""")
  }

  test("can inject Option field values") {
    val vOpt = Some(1)
    val vOpt2 = None
    check(json"{a:? $vOpt}", """{"a": 1}""")
    check(json"{a:? $vOpt2}", """{}""")

    val k = "a"
    check(json"{$k:? $vOpt}", """{"a": 1}""")
    check(json"{$k:? $vOpt2}", """{}""")
  }

  test("can inject Option field values with implicit Writes") {
    val vOpt = Some(Foo("1", "2"))
    check(json"{a:? $vOpt}", """{"a": {"bar": "1", "baz": "2"}}""")
  }

  test("can mix values, Iterables and Options in object") {
    val a = List("a" -> 10)
    val b = Some("b" -> 20)
    val c = Nil
    val d = None
    check(
      json"""{i:1, ..$a, j:2, ..$b, k:3, ..$c, l:4, ..$d, m:5}""",
      """{"i":1, "a":10, "j":2, "b":20, "k":3, "l":4, "m":5}"""
    )
  }

  test("can nest jsonquote templates") {

    val jsonObject = Obj(
      "users" -> Seq(
        Obj(
          "name" -> Str("Bob"),
          "age" -> Num(31),
          "email" -> Str("bob@gmail.com")
        ),
        Obj(
          "name" -> Str("Kiki"),
          "age" -> Num(25)
        )
      )
    )

    val users = Seq(("Bob", 31, Some("bob@gmail.com")), ("Kiki", 25, None))

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

    // upickle already knows how to convert Seq[Value] to json array
    // still need the .toSeq here
    val quoteB = json"""{
      users: ${
        users.map { case (name, age, email) =>
          json"""{
            name: $name,
            age: $age,
            email:? $email
          }"""
        }.toSeq
      }
    }"""

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
    quoteB should equal (jsonObject)
    quoteC should equal (jsonObject)
  }
}
