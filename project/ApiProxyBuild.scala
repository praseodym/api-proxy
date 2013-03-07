import sbt._
import sbt.Keys._

object ApiProxyBuild extends Build {

  lazy val apiProxy = Project(
    id = "api-proxy-build",
    base = file("."),
    settings = Project.defaultSettings ++ Seq(
      name := "API Proxy",
      organization := "nl.praseodym",
      version := "0.1-SNAPSHOT",
      scalaVersion := "2.10.0",
      resolvers += "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases",
      libraryDependencies += "com.typesafe.akka" % "akka-actor_2.10" % "2.1.1",
      libraryDependencies += "com.typesafe.akka" % "akka-slf4j_2.10" % "2.1.1",
      resolvers += "spray repo" at "http://repo.spray.io",
      libraryDependencies += "io.spray" % "spray-client" % "1.1-M7",
      libraryDependencies += "io.spray" % "spray-caching" % "1.1-M7",
      libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.0.9"
  )
  )
}
