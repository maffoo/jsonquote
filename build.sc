import mill.scalalib._
import mill.scalalib.api.Util
import mill.scalalib.publish._

object jsonquote extends mill.Cross[JsonQuote]("2.11.12", "2.12.10", "2.13.1")
class JsonQuote(crossVersion: String) extends mill.Module {
  trait Module extends CrossSbtModule with PublishModule {
    def publishVersion = "0.6.1"
    def pomSettings = PomSettings(
      description = "Build json using scala string interpolation",
      organization = "net.maffoo",
      url = "https://github.com/maffoo/jsonquote",
      licenses = Seq(License.MIT),
      versionControl = VersionControl.github("maffoo", "jsonquote"),
      developers = Seq(
        Developer("maffoo", "Matthew Neeley","https://github.com/maffoo")
      )
    )

    def crossScalaVersion = crossVersion
    def scalacOptions = Seq("-feature", "-deprecation")

    def testModuleDeps: Seq[TestModule] = Nil
    object test extends Tests {
      def moduleDeps = super.moduleDeps ++ testModuleDeps
      def ivyDeps = Agg(ivy"org.scalatest::scalatest:3.0.8")
      def testFrameworks = Seq("org.scalatest.tools.Framework")
    }
  }

  object core extends Module {
    override def millSourcePath = super.millSourcePath / os.up / os.up / "core"
    def artifactName = "jsonquote-core"
    def ivyDeps = Agg(ivy"${scalaOrganization()}:scala-reflect:${scalaVersion()}")
  }

  class Interop(name: String, ivyDeps0: Dep*)(implicit ctx: mill.define.Ctx) extends Module {
    override def millSourcePath = super.millSourcePath / os.up / os.up / name
    def artifactName = s"jsonquote-$name"
    def moduleDeps = Seq(core)
    def testModuleDeps = Seq(core.test)
    def ivyDeps = Agg.from(ivyDeps0)
    def scalaBinaryVersion = mill.T { Util.scalaBinaryVersion(scalaVersion()) }
  }

  object json4s extends Interop("json4s", ivy"org.json4s::json4s-native:3.6.7")

  object lift extends Interop("lift", ivy"net.liftweb::lift-json:3.4.0")

  object play extends Interop("play") {
    def ivyDeps = mill.T {
      val playVersion = scalaBinaryVersion() match {
        case "2.11" => "2.7.4"
        case _ => "2.8.0"
      }
      Agg(ivy"com.typesafe.play::play-json:${playVersion}")
    }
  }

  object spray extends Interop("spray", ivy"io.spray::spray-json:1.3.5")

  object upickle extends Interop("upickle") {
    def ivyDeps = mill.T {
      val upickleVersion = scalaBinaryVersion() match {
        case "2.11" => "0.7.4"
        case _ => "0.8.0"
      }
      Agg(
        ivy"com.lihaoyi::upickle-core:${upickleVersion}",
        ivy"com.lihaoyi::upickle:${upickleVersion}"
      )
    }
  }
}
