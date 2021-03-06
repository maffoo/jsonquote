package net.maffoo.jsonquote.play

import _root_.play.api.libs.json._
import org.scalatest.{FunSuite, Matchers}

case class Foo(bar: String, baz: String)

class PlayTest extends FunSuite with Matchers {

  implicit val fooFormat = Json.writes[Foo]

  def check(js: JsValue, s: String): Unit = { js should equal (Json.parse(s)) }

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
    val foo = Foo(bar = "a", baz = "b")
    check(json"$foo", """{"bar": "a", "baz": "b"}""")
  }

  test("can inject values with implicit Writes") {
    val foos = List(Foo("1", "2"), Foo("3", "4"))
    check(json"[..$foos]", """[{"bar":"1", "baz":"2"}, {"bar":"3", "baz":"4"}]""")
    check(json"[..$Nil]", """[]""")
  }

  test("can inject Option values") {
    val vOpt = Some(JsNumber(1))
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

  test("can inject multiple fields") {
    val kvs = Seq("a" -> 1, "b" -> 2)
    check(json"{..$kvs}", """{"a": 1, "b": 2}""")
    check(json"{..$Nil}", """{}""")
  }

  test("can inject just a field name") {
    val key = "foo"
    check(json"{$key: 1}", """{"foo": 1}""")
  }

  test("can inject Option fields") {
    val kvOpt = Some("a" -> JsNumber(1))
    check(json"{..$kvOpt}", """{"a": 1}""")
    check(json"{..$None}", """{}""")
  }

  test("can inject Option fields with implicit Writes") {
    val kvOpt = Some("a" -> Foo("1", "2"))
    check(json"{..$kvOpt}", """{"a": {"bar": "1", "baz": "2"}}""")
  }

  test("can inject Option field values") {
    val vOpt = Some(JsNumber(1))
    check(json"{a:? $vOpt}", """{"a": 1}""")
    check(json"{a:? $None}", """{}""")

    val k = "a"
    check(json"{$k:? $vOpt}", """{"a": 1}""")
    check(json"{$k:? $None}", """{}""")
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

    // example taken from the play docs: http://www.playframework.com/documentation/2.1.x/ScalaJson
    import play.api.libs.json.Json.toJson

    val jsonObject = toJson(
      Map(
        "users" -> Seq(
          toJson(
            Map(
              "name" -> toJson("Bob"),
              "age" -> toJson(31),
              "email" -> toJson("bob@gmail.com")
            )
          ),
          toJson(
            Map(
              "name" -> toJson("Kiki"),
              "age" -> toJson(25),
              "email" -> JsNull
            )
          )
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
            email: $email
          }"""
        }.toSeq
      }]
    }"""

    // play already knows how to convert Seq[JsValue] to json array
    // still need the .toSeq here
    val quoteB = json"""{
      users: ${
        users.map { case (name, age, email) =>
          json"""{
            name: $name,
            age: $age,
            email: $email
          }"""
        }.toSeq
      }
    }"""

    // types inferred properly here
    val mapped = users.map { case (name, age, email) =>
      json"""{
        name: $name,
        age: $age,
        email: $email
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
