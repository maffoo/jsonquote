val commonSettings = Seq(
  version := "0.5.0",
  organization := "net.maffoo",
  scalaVersion := "2.12.1",
  crossScalaVersions := Seq("2.10.6", "2.11.8", "2.12.1"),
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
  libraryDependencies ++= {
    scalaBinaryVersion.value match {
      case "2.10" => Seq(
        "org.scalamacros" %% "quasiquotes" % "2.1.0",
        compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
      )
      case _ => Nil
    }
  },
  EclipseKeys.eclipseOutput := Some(".eclipse-target"),
  bintrayOrganization := Some("maffoo")
)

lazy val root = project.in(file("."))
  .aggregate(core, lift, play, spray)
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
      "org.scalatest" %% "scalatest" % "3.0.1" % "test"
    )
  )

lazy val lift = project.in(file("lift"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(
    commonSettings,
    name := "jsonquote-lift",
    libraryDependencies += (scalaBinaryVersion.value match {
      case "2.10" => "net.liftweb" %% "lift-json" % "2.6.3"
      case _ => "net.liftweb" %% "lift-json" % "3.0.1"
    })
  )

lazy val play = project.in(file("play"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(
    commonSettings,
    name := "jsonquote-play",
    resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
    libraryDependencies += (scalaBinaryVersion.value match {
      case "2.10" => "com.typesafe.play" %% "play-json" % "2.4.8"
      case "2.11" => "com.typesafe.play" %% "play-json" % "2.5.4"
      case _      => "com.typesafe.play" %% "play-json" % "2.6.2"
    })
  )

lazy val spray = project.in(file("spray"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(
    commonSettings,
    name := "jsonquote-spray",
    resolvers += "Spray repository" at "http://repo.spray.io",
    libraryDependencies += "io.spray" %% "spray-json" % "1.3.3"
  )
