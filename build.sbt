name := """convai-testing-system"""

organization := "ai.ipavlov"

version := "0.1-" + sys.env.getOrElse("BUILD_NUMBER", "SNAPSHOT")

scalaVersion := "2.12.3"

maintainer := "Vadim Polulyakh <bavadim@gmail.com>"

packageSummary := "The convai and turing hackathon testing system"

packageDescription := """The best of the best tool for nlp dataset collection"""

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.4.18",
  "info.mukel" %% "telegrambot4s" % "3.0.0",
  "org.slf4j" % "slf4j-log4j12" % "1.7.25",
  "com.typesafe.akka" %% "akka-http-spray-json" % "10.0.8",
  "org.mongodb.scala" %% "mongo-scala-driver" % "2.1.0",
  "com.typesafe.akka" %% "akka-slf4j" % "2.4.18",
  "org.json4s" %% "json4s-jackson" % "3.5.3",
  "com.typesafe.akka" %% "akka-testkit" % "2.4.18" % Test,
  "org.scalatest" %% "scalatest" % "3.0.3" % Test
)

enablePlugins(JavaServerAppPackaging)

enablePlugins(DebianPlugin)

enablePlugins(SystemVPlugin)

debianPackageDependencies in Debian ++= Seq("java2-runtime", "bash (>= 2.05a-11)")

javaOptions in Universal ++= Seq(
  // -J params will be added as jvm parameters
  "-J-Xmx512m",
  "-J-Xms512m",
  s"-Dconfig.file=/etc/${name.value}/reference.conf",
  s"-Dlog4j.configuration=/etc/${name.value}/log4j.properties"
)

enablePlugins(BuildInfoPlugin)

buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion)

buildInfoPackage := "org.pavlovai"

resolvers += Resolver.jcenterRepo

licenses += ("MIT", url("https://www.apache.org/licenses/LICENSE-2.0"))

publish := {
  import sys.process._
  val stdout = new StringBuilder
  val accessToken = sys.env.getOrElse("github_access_token", "")
  val fname = s"${name.value + "_" + version.value}_all.deb"

  val json = s"""{"tag_name":"${version.value}","target_commitish":"master","name":"$fname","body":"${packageSummary.value}","draft":true,"prerelease":false}"""
  val releaseUrl = s"https://api.github.com/repos/deepmipt/convai-testing-system/releases?access_token=$accessToken"

  Process("curl" :: "-X" :: "POST" :: releaseUrl :: "-d" :: json :: Nil, baseDirectory.value) #|
    Process("jq" :: ".upload_url" :: Nil, baseDirectory.value) #|
    Process("sed" :: "s/{?name,label}//g;s/\"//g" :: Nil, baseDirectory.value) ! ProcessLogger(stdout append _, (_) => ())

  val uploadUrl = stdout.append(s"?name=$fname&access_token=$accessToken").toString()

  s"/usr/bin/curl -F upload=@target/$fname $uploadUrl" !
}

linuxPackageMappings ++= Seq(
  packageMapping(sourceDirectory.value / "main" / "resources" / "reference.conf" -> "conf/reference.conf"),
  packageMapping(sourceDirectory.value / "main" / "resources" / "log4j.properties" -> "conf/log4j.properties")
)




