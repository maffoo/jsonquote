jsonquote
=========

jsonquote is a little library that lets you build JSON in Scala using string interpolation.
It uses macros to parse and validate your json at compile time, and to ensure that the values
you are trying to interpolate are of the appropriate types for the places in the json where
they are to be interpolated. Currently, jsonquote supports play json, with support for other
json libraries like lift and spray in the works.

Here are some examples of how the interpolation works. First, import everything:
```scala
scala> import net.maffoo.jsonquote.play._
import net.maffoo.jsonquote.play._

scala> import play.api.libs.json._
import play.api.libs.json._
```

Now, we can interpolate single values:
```scala
scala> val hi = "Hello, world!"
hi: String = Hello, world!

scala> json"$hi"
res0: play.api.libs.json.JsValue = "Hello, world!"

scala> json"[$hi, $hi]"
res1: play.api.libs.json.JsValue = ["Hello, world!","Hello, world!"]

scala> json"{greeting: $hi}"
res2: play.api.libs.json.JsValue = {"greeting":"Hello, world!"}
```

field names:
```scala
scala> val foo = "bar"
foo: String = bar

scala> json"{$foo: 123}"
res3: play.api.libs.json.JsValue = {"bar":123}
```

key-value pairs in objects:
```scala
scala> val item = "msg" -> "yippee!"
item: (String, String) = (msg,yippee!)

scala> json"{$item}"
res4: play.api.libs.json.JsValue = {"msg":"yippee!"}
```

We can also interpolate multiple values or fields from Iterable or Option all at once:
```scala
scala> val numbers = List(1,2,3,4,5)
numbers: List[Int] = List(1, 2, 3, 4, 5)

scala> json"[*$numbers]"
res5: play.api.libs.json.JsValue = [1,2,3,4,5]

scala> val (a, b) = (Some("a" -> "here"), None)
a: Some[(String, String)] = Some((a,here))
b: None.type = None

scala> json"{*$a, *$b}"
res6: play.api.libs.json.JsValue = {"a":"here"}
```

Variables of type JsValue are interpolated as-is:
```scala
scala> val list = json"[1,2,3,4]"
list: play.api.libs.json.JsValue = [1,2,3,4]

scala> json"{list: $list}"
res7: play.api.libs.json.JsValue = {"list":[1,2,3,4]}
```

Other variables are implicitly converted to JsValue and the compiler
will complain if there is no implicit conversion in scope:
```scala
scala> case class Foo(a: String, b: Int)
defined class Foo

scala> val foo = Foo("a", 1)
foo: Foo = Foo(a,1)

scala> json"{foo: $foo}"
<console>:17: error: could not find implicit value of type Writes[Foo]
              json"{foo: $foo}"
                          ^

scala> implicit val fooFormat = Json.writes[Foo]
fooFormat: play.api.libs.json.OWrites[Foo] = play.api.libs.json.OWrites$$anon$2@2f2b6ef9

scala> json"{foo: $foo}"
res9: play.api.libs.json.JsValue = {"foo":{"a":"a","b":1}}
```

Even without any interpolation, we get compile-time checking of our json literals, with
some syntactic niceties like the ability to omit quotes around field names:
```scala
scala> val list = json"[1, 2, 3"
error: exception during macro expansion: 
java.lang.IllegalArgumentException: requirement failed: expected ',' but got EOF

scala> json"{ a: 1, b: 2 }"
res18: play.api.libs.json.JsValue = {"a":1,"b":2}
```
