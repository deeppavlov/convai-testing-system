name := """convai-testing-system"""

version := "0.1-SNAPSHOT"

scalaVersion := "2.12.2"

maintainer := "Vadim Polulyakh <bavadim@gmail.com>"

packageSummary := "The convai and turing hackathon testing system"

packageDescription := """The best of the best tool for nlp dataset collection"""

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.4.18",
  "info.mukel" %% "telegrambot4s" % "3.0.0",
  "org.slf4j" % "slf4j-log4j12" % "1.7.25",
  "com.typesafe.akka" %% "akka-http-spray-json" % "10.0.8",
  "org.mongodb.scala" %% "mongo-scala-driver" % "2.1.0",
  "com.typesafe.akka" %% "akka-testkit" % "2.4.18" % Test,
  "org.scalatest" %% "scalatest" % "3.0.3" % Test
)

enablePlugins(JavaAppPackaging)

enablePlugins(DebianPlugin)

debianPackageDependencies in Debian ++= Seq("java2-runtime", "bash (>= 2.05a-11)")

javaOptions in Universal ++= Seq(
  // -J params will be added as jvm parameters
  "-J-Xmx512m",
  "-J-Xms512m"
)

enablePlugins(BuildInfoPlugin)

buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion)

buildInfoPackage := "org.pavlovai"
