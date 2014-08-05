jsonquote
=========

jsonquote is a little library that lets you build JSON in Scala using string interpolation.
It uses macros to parse and validate your json at compile time, and to ensure that the values
you are trying to interpolate are of the appropriate types for the places in the json where
they are to be interpolated. jsonquote supports play-json, spray-json, and lift-json.


Using jsonquote
---------------

jsonquote is published on bintray. To include it in your project, simply add the desired
artifact as a maven dependency for the json library you would like to use:
```scala
resolvers += "bintray-jcenter" at "http://jcenter.bintray.com/"

// use the basic 'literal' json support built in to jsonquote
libraryDependencies += "net.maffoo" %% "jsonquote-core" % "0.1.6"

// use one of the supported third-party json libraries
libraryDependencies += "net.maffoo" %% "jsonquote-lift" % "0.1.6"

libraryDependencies += "net.maffoo" %% "jsonquote-play" % "0.1.6"

libraryDependencies += "net.maffoo" %% "jsonquote-spray" % "0.1.6"

```


Examples
--------

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

We can also interpolate multiple values or fields from Iterable or Option all at once
(the .. syntax here was chosen to mirror that used in quasiquotes for scala macros):
```scala
scala> val numbers = List(1,2,3,4,5)
numbers: List[Int] = List(1, 2, 3, 4, 5)

scala> json"[..$numbers]"
res5: play.api.libs.json.JsValue = [1,2,3,4,5]

scala> val (a, b) = (Some("a" -> "here"), None)
a: Some[(String, String)] = Some((a,here))
b: None.type = None

scala> json"{..$a, ..$b}"
res6: play.api.libs.json.JsValue = {"a":"here"}
```

Note that when interpolating multiple values, you may see type errors if you try to
map over collections. This is due to interactions between scala's type inference
on collection builders, macro defs, and the StringContext method call into which
the compiler transforms the interpolated string. This requires giving the compiler
a hint about what concrete collection type you want:
```scala
scala> val xs = Seq("a" -> 1, "b" -> 2)
xs: Seq[(String, Int)] = List((a,1), (b,2))

scala> json"[..${xs.map(_._1)}]"
<console>:15: error: required Iterable[_] but got Any
              json"[..${xs.map(_._1)}]"
                              ^

scala> json"[..${xs.map(_._1).toSeq}]"
res1: play.api.libs.json.JsArray = ["a","b"]
```


Alternately, we can define optional fields with a name but optional value that
will be dropped from the final result if the value is None:
```scala
scala> val (some, none) = (Some("hello!"), None)
some: Some[String] = Some(hello!)
none: None.type = None

scala> json"{msg:? $some, msg2:? $none}"
res7: play.api.libs.json.JsObject = {"msg":"hello!"}
```

Variables of type JsValue are interpolated as-is:
```scala
scala> val list = json"[1,2,3,4]"
list: play.api.libs.json.JsValue = [1,2,3,4]

scala> json"{list: $list}"
res8: play.api.libs.json.JsValue = {"list":[1,2,3,4]}
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
some syntactic niceties like the ability to omit quotes around field names, and inline
comments in your json literals:
```scala
scala> val list = json"[1, 2, 3"
error: exception during macro expansion: 
java.lang.IllegalArgumentException: requirement failed: expected ',' but got EOF

scala> json"{ a: 1, b: 2 }"
res18: play.api.libs.json.JsValue = {"a":1,"b":2}

scala> json"{ a: 1 /* this is awesome */, b: 2 /* this is, too */ } // that was great"
res19: play.api.libs.json.JsValue = {"a":1,"b":2}
```
