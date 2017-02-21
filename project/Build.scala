import sbt._
import sbt.Keys._
import bintray.Plugin._
import com.typesafe.sbteclipse.plugin.EclipsePlugin.EclipseKeys

object BuildSettings {

  val buildSettings = Defaults.defaultSettings ++ Seq(
    version := "0.4.0",
    organization := "net.maffoo",
    scalaVersion := "2.12.1",
    crossScalaVersions := Seq("2.10.6", "2.11.8", "2.12.1"),
    scalaOrganization := "org.scala-lang",
    licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
    resolvers += Resolver.sonatypeRepo("snapshots"),
    resolvers += Resolver.sonatypeRepo("releases"),
    scalacOptions ++= Seq("-feature", "-deprecation"),
    unmanagedSourceDirectories in Compile <+= (scalaBinaryVersion, sourceDirectory in Compile) { (v, dir) =>
      dir / s"scala-$v"
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
    EclipseKeys.eclipseOutput := Some(".eclipse-target")
  )
}

object MyBuild extends Build {
  import BuildSettings._

  lazy val root = Project(
    "jsonquote",
    file("."),
    settings = buildSettings ++ Seq(
      publish := {}
    )
  ).aggregate(
    core, lift, play, spray
  )

  lazy val core = Project(
    "jsonquote-core",
    file("core"),
    settings = buildSettings ++ bintraySettings ++ Seq(
      libraryDependencies ++= Seq(
        scalaOrganization.value % "scala-reflect" % scalaVersion.value,
        "org.scalatest" %% "scalatest" % "3.0.1" % "test"
      )
    )
  )

  lazy val lift = Project(
    "jsonquote-lift",
    file("lift"),
    settings = buildSettings ++ bintraySettings ++ Seq(
      libraryDependencies += (scalaBinaryVersion.value match {
        case "2.10" => "net.liftweb" %% "lift-json" % "2.6.3"
        case _ => "net.liftweb" %% "lift-json" % "3.0.1"
      })
    )
  ) dependsOn(core % "compile->compile;test->test")

  lazy val play = Project(
    "jsonquote-play",
    file("play"),
    settings = buildSettings ++ bintraySettings ++ Seq(
      resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
      libraryDependencies += (scalaBinaryVersion.value match {
        case "2.10" => "com.typesafe.play" %% "play-json" % "2.4.8"
        case "2.11" => "com.typesafe.play" %% "play-json" % "2.5.4"
        case _      => "com.typesafe.play" %% "play-json" % "2.6.0-M3"
      })
    )
  ) dependsOn(core % "compile->compile;test->test")

  lazy val spray = Project(
    "jsonquote-spray",
    file("spray"),
    settings = buildSettings ++ bintraySettings ++ Seq(
      resolvers += "Spray repository" at "http://repo.spray.io",
      libraryDependencies += "io.spray" %% "spray-json" % "1.3.3"
    )
  ) dependsOn(core % "compile->compile;test->test")
}
