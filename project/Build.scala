import sbt._
import sbt.Keys._
import bintray.Plugin._
import com.typesafe.sbteclipse.plugin.EclipsePlugin.EclipseKeys

object BuildSettings {
  val buildVersion = "0.1.7"
  val buildScalaVersion = "2.11.1"
  val buildScalaOrganization = "org.scala-lang"

  val buildSettings = Defaults.defaultSettings ++ Seq(
    version := buildVersion,
    organization := "net.maffoo",
    scalaVersion := buildScalaVersion,
    crossScalaVersions := Seq(scalaVersion.value),
    scalaOrganization := buildScalaOrganization,
    licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
    resolvers += Resolver.sonatypeRepo("snapshots"),
    resolvers += Resolver.sonatypeRepo("releases"),
    scalacOptions ++= Seq("-feature", "-deprecation"),
    unmanagedSourceDirectories in Compile <+= (scalaBinaryVersion, sourceDirectory in Compile) { (v, dir) =>
      dir / s"scala-$v"
    },
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.0.0" cross CrossVersion.full),
    EclipseKeys.eclipseOutput := Some(".eclipse-target")
  )
}

object MyBuild extends Build {
  import BuildSettings._

  lazy val root = Project("jsonquote", file("."), settings = buildSettings).aggregate(
    core, lift, play, spray
  )

  lazy val core = Project(
    "jsonquote-core",
    file("core"),
    settings = buildSettings ++ bintraySettings ++ Seq(
      libraryDependencies ++= Seq(
        scalaOrganization.value % "scala-reflect" % scalaVersion.value,
        "org.scalatest" %% "scalatest" % "2.2.0" % "test"
      )
    )
  )

  lazy val lift = Project(
    "jsonquote-lift",
    file("lift"),
    settings = buildSettings ++ bintraySettings ++ Seq(
      libraryDependencies += "net.liftweb" %% "lift-json" % "2.6-M4"
    )
  ) dependsOn(core % "compile->compile;test->test")

  lazy val play = Project(
    "jsonquote-play",
    file("play"),
    settings = buildSettings ++ bintraySettings ++ Seq(
      resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
      libraryDependencies += "com.typesafe.play" %% "play-json" % "2.3.1"
    )
  ) dependsOn(core % "compile->compile;test->test")

  lazy val spray = Project(
    "jsonquote-spray",
    file("spray"),
    settings = buildSettings ++ bintraySettings ++ Seq(
      resolvers += "Spray repository" at "http://repo.spray.io",
      libraryDependencies += "io.spray" %% "spray-json" % "1.2.6"
    )
  ) dependsOn(core % "compile->compile;test->test")
}
