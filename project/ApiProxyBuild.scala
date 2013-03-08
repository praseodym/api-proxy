import sbt._
import sbt.Keys._
import com.github.retronym.SbtOneJar

object ApiProxyBuild extends Build {
  val akkaVersion = "2.1.1"
  val sprayVersion = "1.1-20130207"

  lazy val apiProxy = Project(
    id = "api-proxy",
    base = file("."),
    settings = Project.defaultSettings ++ SbtOneJar.oneJarSettings ++ Seq(
      name := "API Proxy",
      organization := "nl.praseodym",
      version := "0.1-SNAPSHOT",
      scalaVersion := "2.10.0",
      resolvers += "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases",
      resolvers += "spray repo" at "http://nightlies.spray.io",
      libraryDependencies ++= Seq(
        "ch.qos.logback" % "logback-classic" % "1.0.9",
        "com.typesafe.akka" %% "akka-actor" % akkaVersion,
        "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
        "io.spray" % "spray-can" % sprayVersion,
        "io.spray" % "spray-client" % sprayVersion,
        "io.spray" % "spray-caching" % sprayVersion
      )
    )
  )
}
