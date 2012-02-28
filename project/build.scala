import sbt._
import Keys._


object ScalaConduitsBuild extends Build {

  lazy val root = Project(
    id = "scala-conduits",
    base = file("."),
    settings = standardSettings,
    aggregate = Seq(conduits, examples)
  )

  lazy val conduits = Project(
    id = "conduits",
    base = file("conduits"),
    settings = standardSettings ++ Seq(
      libraryDependencies ++= Seq(Dependencies.scalaz, Dependencies.scalazEffect, Dependencies.ScalaCheck, Dependencies.Specs)
    )
  )

//  lazy val conduits = Project(
//    id = "conduits",
//    base = file("conduits"),
//    settings = standardSettings ++ Seq(
//      libraryDependencies ++= Seq(Dependencies.scalaz, Dependencies.scalazEffect, Dependencies.ScalaCheck, Dependencies.Specs)
//    )
//  )

  lazy val examples = Project(
    id = "conduits-examples",
    base = file("examples"),
    dependencies = Seq[ClasspathDep[ProjectReference]](conduits),
    settings = standardSettings
  )

  lazy val standardSettings = Defaults.defaultSettings ++ Seq(
    organization := "org.ulysses.data",
    version := "0.1-SNAPSHOT",
//    scalaVersion := "2.9.1",
    scalaVersion := "2.10.0-M2",
    scalacOptions  ++= Seq("-encoding", "UTF-8", "-deprecation", "-unchecked"),
    resolvers += ScalaToolsSnapshots
  )

  object Dependencies {
    lazy val scalaz = "org.scalaz" % "scalaz-core_2.9.1" % "7.0-SNAPSHOT"
    lazy val scalazEffect = "org.scalaz" % "scalaz-effect_2.9.1" % "7.0-SNAPSHOT"
    //lazy val specs = "org.scala-tools.testing" %% "specs" % "1.6.7" % "test" withSources ()
    lazy val scalacheck = "org.scala-tools.testing" % "scalacheck_2.8.1" % "1.8" % "test"

    def ScalaCheck = "org.scala-tools.testing" % "scalacheck_2.9.1" % "1.9" % "test"

    def Specs = "org.specs2" % "specs2_2.9.1" % "1.6.1" % "test"
  }
}
