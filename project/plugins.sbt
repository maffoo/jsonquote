name := "jsonquote-project"

addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "2.4.0")

EclipseKeys.eclipseOutput := Some(".eclipse-target")

// setup bintray
addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.4.0")
