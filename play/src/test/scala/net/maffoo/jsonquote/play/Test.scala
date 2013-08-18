package net.maffoo.jsonquote.play

import _root_.play.api.libs.json._
import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers

case class Foo(bar: String, baz: String)

class PlayTest extends FunSuite with ShouldMatchers {

  implicit val fooFormat = Json.writes[Foo]

  def check(js: JsValue, s: String): Unit = { js should equal (Json.parse(s)) }

  test("can parse plain json") {
    check(json""" "hello!" """, """"hello!"""")
  }

  test("can use bare identifiers for object keys") {
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

    // TODO: why do we need the : Seq[JsValue] type ascription here?
    val quoteA = json"""{
      users: [..${
        users.map { case (name, age, email) =>
          json"""{
            name: $name,
            age: $age,
            email: $email
          }"""
        }: Seq[JsValue]
      }]
    }"""

    // play already knows how to convert Seq[JsValue] to json array
    // still need the type ascription here
    val quoteB = json"""{
      users: ${
        users.map { case (name, age, email) =>
          json"""{
            name: $name,
            age: $age,
            email: $email
          }"""
        }: Seq[JsValue]
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
