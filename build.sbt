val commonSettings = Seq(
  version := "0.6.0",
  organization := "net.maffoo",
  scalaVersion := "2.13.1",
  crossScalaVersions := Seq("2.11.12", "2.12.10", "2.13.1"),
  scalaOrganization := "org.scala-lang",
  licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
  resolvers += Resolver.sonatypeRepo("snapshots"),
  resolvers += Resolver.sonatypeRepo("releases"),
  scalacOptions ++= Seq("-feature", "-deprecation"),
  unmanagedSourceDirectories in Compile += {
    val dir = (sourceDirectory in Compile).value
    val version = scalaBinaryVersion.value
    dir / s"scala-$version"
  },
  bintrayOrganization := Some("maffoo")
)

lazy val root = project.in(file("."))
  .aggregate(core, json4s, lift, play, spray, upickle)
  .settings(
    commonSettings,
    name := "jsonquote",
    publish := {}
  )

lazy val core = project.in(file("core"))
  .settings(
    commonSettings,
    name := "jsonquote-core",
    libraryDependencies ++= Seq(
      scalaOrganization.value % "scala-reflect" % scalaVersion.value,
      "org.scalatest" %% "scalatest" % "3.0.8" % "test"
    )
  )

lazy val json4s = project.in(file("json4s"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(
    commonSettings,
    name := "jsonquote-json4s",
    libraryDependencies += "org.json4s" %% "json4s-native" % "3.6.7"
  )

lazy val lift = project.in(file("lift"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(
    commonSettings,
    name := "jsonquote-lift",
    libraryDependencies += "net.liftweb" %% "lift-json" % "3.4.0"
  )

lazy val play = project.in(file("play"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(
    commonSettings,
    name := "jsonquote-play",
    libraryDependencies += (scalaBinaryVersion.value match {
      case "2.11" => "com.typesafe.play" %% "play-json" % "2.7.4"
      case _      => "com.typesafe.play" %% "play-json" % "2.8.0"
    })
  )

lazy val spray = project.in(file("spray"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(
    commonSettings,
    name := "jsonquote-spray",
    libraryDependencies += "io.spray" %% "spray-json" % "1.3.5"
  )

lazy val upickle = project.in(file("upickle"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(
    commonSettings,
    name := "jsonquote-upickle",
    libraryDependencies ++= {
      val upickleVersion = scalaBinaryVersion.value match {
        case "2.11" => "0.7.4"
        case _ => "0.8.0"
      }
      Seq(
        "com.lihaoyi" %% "upickle-core" % upickleVersion,
        "com.lihaoyi" %% "upickle" % upickleVersion
      )
    }
  )
