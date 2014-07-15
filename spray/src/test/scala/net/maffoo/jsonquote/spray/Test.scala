package net.maffoo.jsonquote.spray

import _root_.spray.json._
import _root_.spray.json.DefaultJsonProtocol._
import org.scalatest.{FunSuite, Matchers}

case class Foo(bar: String, baz: String)

class SprayTest extends FunSuite with Matchers {

  implicit object FooFormat extends JsonWriter[Foo] {
    def write(x: Foo) = JsObject(Map("bar" -> JsString(x.bar), "baz" -> JsString(x.baz)))
  }

  def check(js: JsValue, s: String): Unit = { js should equal (s.parseJson) }

  //case class Baz(s: String)
  //json"""{test: ${Baz("test")}}"""

  test("can parse plain json") {
    check(json""" "hello!" """, """"hello!"""")
  }

  test("can use bare identifiers for object keys") {
    check(json"{ test: 1 }", """{ "test": 1 }""")
    check(json"{ test-2: 2 }", """{ "test-2": 2 }""")
    check(json"{ test_3: 3 }", """{ "test_3": 3 }""")
    check(json"{ _test-4: 4 }", """{ "_test-4": 4 }""")
  }

  test("can inject value with implicit JsonWriter") {
    val foo = Foo(bar = "a", baz = "b")
    check(json"$foo", """{"bar": "a", "baz": "b"}""")
  }

  test("can inject values with implicit JsonWriter") {
    val foos = List(Foo("1", "2"), Foo("3", "4"))
    check(json"[..$foos]", """[{"bar":"1", "baz":"2"}, {"bar":"3", "baz":"4"}]""")
    check(json"[..$Nil]", """[]""")
  }

  test("can inject Option values") {
    val vOpt = Some(JsNumber(1))
    check(json"[..$vOpt]", """[1]""")
    check(json"[..$None]" , """[]""")
  }

  test("can inject Option values with implicit JsonWriter") {
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

  test("can inject Option fields with implicit JsonWriter") {
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
      json"""{i1:1, ..$a, i2:2, ..$b, i3:3, ..$c, i4:4, ..$d, i5:5}""",
      """{"i1":1, "a":10, "i2":2, "b":20, "i3":3, "i4":4, "i5":5}"""
    )
  }

  test("can nest jsonquote templates") {

    // adapted from the play docs: http://www.playframework.com/documentation/2.1.x/ScalaJson

    val jsonObject = JsObject(
      Map(
        "users" -> JsArray(
          JsObject(
            Map(
              "name" -> JsString("Bob"),
              "age" -> JsNumber(31),
              "email" -> JsString("bob@gmail.com")
            )
          ),
          JsObject(
            Map(
              "name" -> JsString("Kiki"),
              "age" -> JsNumber(25),
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

    // spray already knows how to convert Seq[JsValue] to json array
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
