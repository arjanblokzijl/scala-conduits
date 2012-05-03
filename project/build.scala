import sbt._
import Keys._


object ScalaConduitsBuild extends Build {

  lazy val root = Project(
    id = "scala-conduits",
    base = file("."),
    settings = standardSettings,
    aggregate = Seq(resourcet, bytestring, text, conduits, examples, benchmark)
  )

  lazy val conduits = Project(
    id = "conduits",
    base = file("conduits"),
    dependencies = Seq[ClasspathDep[ProjectReference]](resourcet, bytestring, text),
    settings = standardSettings ++ Seq(
      libraryDependencies ++= Seq(Dependencies.scalaz, Dependencies.scalazEffect, Dependencies.ScalaCheck, Dependencies.Specs)
    )
  )

  lazy val bytestring = Project(
    id = "bytestring",
    base = file("bytestring"),
    dependencies = Seq[ClasspathDep[ProjectReference]](resourcet),
    settings = standardSettings ++ Seq(
      libraryDependencies ++= Seq(Dependencies.scalaz, Dependencies.scalazEffect, Dependencies.ScalaCheck, Dependencies.Specs)
    )
  )

  lazy val text = Project(
    id = "text",
    base = file("text"),
    dependencies = Seq[ClasspathDep[ProjectReference]](bytestring),
    settings = standardSettings ++ Seq(
      libraryDependencies ++= Seq(Dependencies.scalaz, Dependencies.scalazEffect, Dependencies.ScalaCheck, Dependencies.Specs)
    )
  )

  lazy val parse = Project(
    id = "parse",
    base = file("parse"),
    dependencies = Seq[ClasspathDep[ProjectReference]](bytestring, text),
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

  lazy val benchmark: Project = Project(
    id = "benchmark",
    base = file("benchmark"),
    dependencies = Seq[ClasspathDep[ProjectReference]](conduits),
    settings = benchmarkSettings
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

  val key = AttributeKey[Boolean]("javaOptionsPatched")

  lazy val benchmarkSettings = standardSettings ++ Seq(
    javaOptions in run += "-Xmx2G",

    libraryDependencies ++= Seq(
      "com.google.guava" % "guava" % "r09",
      "com.google.code.java-allocation-instrumenter" % "java-allocation-instrumenter" % "2.0",
      "com.google.code.caliper" % "caliper" % "1.0-SNAPSHOT" from "http://n0d.es/jars/caliper-1.0-SNAPSHOT.jar",
      "com.google.code.gson" % "gson" % "1.7.1"
    ),

    // enable forking in run
    fork in run := true,

    // custom kludge to get caliper to see the right classpath

    // we need to add the runtime classpath as a "-cp" argument to the `javaOptions in run`, otherwise caliper
    // will not see the right classpath and die with a ConfigurationException
    // unfortunately `javaOptions` is a SettingsKey and `fullClasspath in Runtime` is a TaskKey, so we need to
    // jump through these hoops here in order to feed the result of the latter into the former
    onLoad in Global ~= { previous => state =>
      previous {
        state.get(key) match {
          case None =>
            // get the runtime classpath, turn into a colon-delimited string
            val classPath = Project.runTask(fullClasspath in Runtime in benchmark, state).get._2.toEither.right.get.files.mkString(":")
            // return a state with javaOptionsPatched = true and javaOptions set correctly
            Project.extract(state).append(Seq(javaOptions in (benchmark, run) ++= Seq("-cp", classPath)), state.put(key, true))
          case Some(_) =>
            state // the javaOptions are already patched
        }
      }
    }
   // caliper stuff stolen shamelessly from scala-benchmarking-template
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
