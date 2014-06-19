import sbt._
import sbt.{Build => SbtBuild}
import sbt.Keys._
import scala.scalajs.sbtplugin.ScalaJSPlugin._
import scala.scalajs.sbtplugin.ScalaJSPlugin.ScalaJSKeys._
import sbtunidoc.Plugin._
import sbtunidoc.Plugin.UnidocKeys._

object Build extends SbtBuild {
  val projectVersion = "0.13.0"

  val sharedSettings = Defaults.defaultSettings ++ Seq(
    organization := "org.monifu",
    version := projectVersion,

    scalaVersion := "2.10.4",
    scalaVersion in ThisBuild := "2.10.4",
    crossScalaVersions ++= Seq("2.11.1"),

    initialize := {
       val _ = initialize.value // run the previous initialization
       val classVersion = sys.props("java.class.version")
       val specVersion = sys.props("java.specification.version")
       assert(
        classVersion.toDouble >= 50 && specVersion.toDouble >= 1.6,
        s"JDK version 6 or newer is required for building this project " + 
        s"(SBT instance running on top of JDK $specVersion with class version $classVersion)")
    },

    scalacOptions ++= Seq(
      "-unchecked", "-deprecation", "-feature", "-Xlint", "-target:jvm-1.6", "-Yinline-warnings",
      "-optimise", "-Ywarn-adapted-args", "-Ywarn-dead-code", "-Ywarn-inaccessible",
      "-Ywarn-nullary-override", "-Ywarn-nullary-unit"
    ),

    scalacOptions <<= baseDirectory.map { bd => Seq("-sourcepath", bd.getAbsolutePath) },
    scalacOptions in (ScalaUnidoc, unidoc) <<= baseDirectory.map { bd =>
      Seq(
        "-Ymacro-no-expand",
        "-sourcepath", bd.getAbsolutePath
      )
    },

    resolvers ++= Seq(
      "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases",
      Resolver.sonatypeRepo("releases")
    ),

    // -- Settings meant for deployment on oss.sonatype.org

    publishMavenStyle := true,

    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value)
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    },

    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false }, // removes optional dependencies
    
    pomExtra :=
      <url>http://www.monifu.org/</url>
      <licenses>
        <license>
          <name>Apache License, Version 2.0</name>
          <url>https://www.apache.org/licenses/LICENSE-2.0</url>
          <distribution>repo</distribution>
        </license>
      </licenses>
      <scm>
        <url>git@github.com:alexandru/monifu.git</url>
        <connection>scm:git:git@github.com:alexandru/monifu.git</connection>
      </scm>
      <developers>
        <developer>
          <id>alex_ndc</id>
          <name>Alexandru Nedelcu</name>
          <url>https://www.bionicspirit.com/</url>
        </developer>
      </developers>
  )

  // -- Actual Projects

  lazy val root = Project(
      id = "monifu-root", base = file("."), 
      settings = sharedSettings ++ Seq(
        publishArtifact := false
      ))
    .aggregate(monifu, monifuJS)

  lazy val monifuCore = Project(
    id = "monifu-core",
    base = file("monifu-core"),
    settings = sharedSettings ++ Seq(
      unmanagedSourceDirectories in Compile <+= sourceDirectory(_ / "shared" / "scala"),
      libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-reflect" % _ % "compile"),
      libraryDependencies ++= Seq(
        "org.scalatest" %% "scalatest" % "2.1.3" % "test"
      )
    )
  )

  lazy val monifuRx = Project(
    id = "monifu-rx",
    base = file("monifu-rx"),
    settings = sharedSettings ++ Seq(
      unmanagedSourceDirectories in Compile <+= sourceDirectory(_ / "shared" / "scala"),
      libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-reflect" % _ % "compile"),
      libraryDependencies ++= Seq(
        "org.scalatest" %% "scalatest" % "2.1.3" % "test"
      )
    )
  ).dependsOn(monifuCore)

  lazy val monifu = Project(id="monifu", base = file("monifu"), 
    settings=sharedSettings ++ unidocSettings ++ Seq(
      unidocProjectFilter in (ScalaUnidoc, unidoc) := inProjects(monifuCore, monifuRx),
      scalacOptions in (ScalaUnidoc, sbtunidoc.Plugin.UnidocKeys.unidoc) ++=
        Opts.doc.sourceUrl(s"https://github.com/alexandru/monifu/tree/v$projectVersion/monifu€{FILE_PATH}.scala"),
      scalacOptions in (ScalaUnidoc, sbtunidoc.Plugin.UnidocKeys.unidoc) ++=
        Opts.doc.title(s"Monifu"),
      scalacOptions in (ScalaUnidoc, sbtunidoc.Plugin.UnidocKeys.unidoc) ++=
        Opts.doc.version(s"$projectVersion"),
      scalacOptions in (ScalaUnidoc, sbtunidoc.Plugin.UnidocKeys.unidoc) ++= 
        Seq("-doc-root-content", "rootdoc.txt")
    ))
    .dependsOn(monifuCore, monifuRx).aggregate(monifuCore, monifuRx)

  lazy val monifuCoreJS = Project(
    id = "monifu-core-js",
    base = file("monifu-core-js"),
    settings = sharedSettings ++ scalaJSSettings ++ Seq(
      unmanagedSourceDirectories in Compile <+= sourceDirectory(_ / ".." / ".." / "monifu-core" / "src" / "shared" / "scala"),
      libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-reflect" % _ % "compile"),
      libraryDependencies ++= Seq(
        "org.scala-lang.modules.scalajs" %% "scalajs-jasmine-test-framework" % scalaJSVersion % "test"
      )
    )
  )

  lazy val monifuRxJS = Project(
    id = "monifu-rx-js",
    base = file("monifu-rx-js"),
    settings = sharedSettings ++ scalaJSSettings ++ Seq(
      unmanagedSourceDirectories in Compile <+= sourceDirectory(_ / ".." / ".." / "monifu-rx" / "src" / "shared" / "scala"),
      libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-reflect" % _ % "compile"),
      libraryDependencies ++= Seq(
        "org.scala-lang.modules.scalajs" %% "scalajs-jasmine-test-framework" % scalaJSVersion % "test"
      )
    )
  ).dependsOn(monifuCoreJS)

  lazy val monifuJS = Project(id="monifu-js", base = file("monifu-js"), 
    settings=sharedSettings ++ unidocSettings ++ (
      unidocProjectFilter in (ScalaUnidoc, unidoc) := inProjects(monifuCoreJS, monifuRxJS)
    ))
    .dependsOn(monifuCoreJS, monifuRxJS).aggregate(monifuCoreJS, monifuRxJS)

  lazy val monifuBenchmarks =
    Project(id="benchmarks", base=file("benchmarks"),
      settings = sharedSettings ++ Seq(publishArtifact := false)).dependsOn(monifu).aggregate(monifu)
}
