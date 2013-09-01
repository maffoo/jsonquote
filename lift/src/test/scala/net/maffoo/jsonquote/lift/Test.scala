package net.maffoo.jsonquote.lift

import net.liftweb.json._
import net.maffoo.jsonquote.lift.Writes._
import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers

case class Foo(bar: String, baz: String)

class LiftTest extends FunSuite with ShouldMatchers {

  implicit val formats = DefaultFormats

  implicit object FooWrites extends Writes[Foo] {
    def write(foo: Foo): JValue = json"{ bar: ${foo.bar}, baz: ${foo.baz} }"
  }

  def check(js: JValue, s: String): Unit = { js should equal (parse(s)) }

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
    val vOpt = Some(JInt(1))
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
    val kvOpt = Some("a" -> JInt(1))
    check(json"{..$kvOpt}", """{"a": 1}""")
    check(json"{..$None}", """{}""")
  }

  test("can inject Option fields with implicit Writes") {
    val kvOpt = Some("a" -> Foo("1", "2"))
    check(json"{..$kvOpt}", """{"a": {"bar": "1", "baz": "2"}}""")
  }

  test("can mix values, Iterables and Options in object") {
    val a = List("a" -> 10)
    val b = Some("b" -> 20)
    val c = Nil
    val d = None
    check(
      json"""{i:1, ..$a, i:2, ..$b, i:3, ..$c, i:4, ..$d, i:5}""",
      """{"i":1, "a":10, "i":2, "b":20, "i":3, "i":4, "i":5}"""
    )
  }

  test("can nest jsonquote templates") {

    // adapted from the play docs: http://www.playframework.com/documentation/2.1.x/ScalaJson
    import net.liftweb.json.JsonDSL._

    val jsonObject =
      JObject(List(
        JField("users",
          JArray(List(
            ("name" -> "Bob") ~ ("age" -> 31) ~ ("email" -> "bob@gmail.com"),
            ("name" -> "Kiki") ~ ("age" -> 25) ~ ("email" -> JNull)
          ))
        )
      ))

    val users = Seq(("Bob", 31, Some("bob@gmail.com")), ("Kiki", 25, None))

    // TODO: why do we need the : Seq[JValue] type ascription here?
    val quoteA = json"""{
      users: [..${
        users.map { case (name, age, email) =>
          json"""{
            name: $name,
            age: $age,
            email: $email
          }"""
        }: Seq[JValue]
      }]
    }"""

    // we defined a Serializer for Seq[JValue]
    // still need the type ascription here
    val quoteB = json"""{
      users: ${
        users.map { case (name, age, email) =>
          json"""{
            name: $name,
            age: $age,
            email: $email
          }"""
        }: Seq[JValue]
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
