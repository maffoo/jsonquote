import sbt._
import sbt.Keys._
import com.typesafe.sbteclipse.plugin.EclipsePlugin.EclipseKeys

object BuildSettings {
  val buildVersion = "0.1.0-SNAPSHOT"
  val buildScalaVersion = "2.10.2"
  val buildScalaOrganization = "org.scala-lang"

  val buildSettings = Defaults.defaultSettings ++ Seq(
    version := buildVersion,
    scalaVersion := buildScalaVersion,
    scalaOrganization := buildScalaOrganization,
    resolvers += Resolver.sonatypeRepo("snapshots"),
    resolvers += Resolver.sonatypeRepo("releases"),
    scalacOptions ++= Seq("-feature", "-deprecation"),
    EclipseKeys.eclipseOutput := Some(".eclipse-target")
  )
}

object MyBuild extends Build {
  import BuildSettings._

  lazy val core = Project(
    "jsonquote-core",
    file("core"),
    settings = buildSettings ++ Seq(
      libraryDependencies ++= Seq(
        scalaOrganization.value % "scala-reflect" % scalaVersion.value,
        "org.scalatest" %% "scalatest" % "2.0.M5b" % "test"
      )
    )
  )

  lazy val play = Project(
    "jsonquote-play",
    file("play"),
    settings = buildSettings ++ Seq(
      resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
      libraryDependencies += "com.typesafe.play" %% "play-json" % "2.2.0-M2"
    )
  ) dependsOn(core % "compile->compile;test->test")
}
