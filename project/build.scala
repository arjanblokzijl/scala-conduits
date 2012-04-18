import sbt._
import Keys._


object ScalaConduitsBuild extends Build {

  lazy val root = Project(
    id = "scala-conduits",
    base = file("."),
    settings = standardSettings,
    aggregate = Seq(resourcet, conduits, examples)
  )

  lazy val conduits = Project(
    id = "conduits",
    base = file("conduits"),
    dependencies = Seq[ClasspathDep[ProjectReference]](resourcet),
    settings = standardSettings ++ Seq(
      libraryDependencies ++= Seq(Dependencies.scalaz, Dependencies.scalazEffect, Dependencies.ScalaCheck, Dependencies.Specs)
    )
  )

  lazy val resourcet = Project(
    id = "resourcet",
    base = file("resourcet"),
    settings = standardSettings ++ Seq(
      libraryDependencies ++= Seq(Dependencies.scalaz, Dependencies.scalazEffect, Dependencies.ScalaCheck, Dependencies.Specs)
    )
  )

  lazy val examples = Project(
    id = "conduits-examples",
    base = file("examples"),
    dependencies = Seq[ClasspathDep[ProjectReference]](conduits),
    settings = standardSettings
  )

  lazy val standardSettings = Defaults.defaultSettings ++ Seq(
    organization := "com.github.ab",
    version := "0.1-SNAPSHOT",
    scalaVersion := "2.9.2",
//    scalaVersion := "2.10.0-M2",
    crossPaths := false,
    scalacOptions  ++= Seq("-encoding", "UTF-8", "-deprecation", "-unchecked"),
    resolvers ++= Seq("releases" at "http://oss.sonatype.org/content/repositories/releases",
                        "snapshots" at "http://oss.sonatype.org/content/repositories/snapshots")
  )

  object Dependencies {
    def scalaz = "org.scalaz" % "scalaz-core_2.9.2" % "7.0-SNAPSHOT"
    def scalazEffect = "org.scalaz" % "scalaz-effect_2.9.2" % "7.0-SNAPSHOT"
    //lazy val specs = "org.scala-tools.testing" %% "specs" % "1.6.7" % "test" withSources ()
    def scalacheck = "org.scala-tools.testing" % "scalacheck_2.8.1" % "1.8" % "test"

    def ScalaCheck = "org.scala-tools.testing" % "scalacheck_2.9.1" % "1.9" % "test"

    def Specs = "org.specs2" % "specs2_2.9.1" % "1.6.1" % "test"
  }
}
