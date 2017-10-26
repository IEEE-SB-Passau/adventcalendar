name := """ieee-passau-advent-frontend"""

version := "2016-BASE"

scalaVersion := "2.11.8"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalacOptions in ThisBuild ++= Seq("-unchecked", "-deprecation", "-feature")

libraryDependencies ++= Seq(
  ws,
  jdbc,
  anorm,
  cache,
  filters,
  "com.typesafe.play" %% "play" % "2.3.10",
  "com.typesafe.play" %% "play-slick" % "0.8.0",
  "com.typesafe.slick" %% "slick" % "2.1.0",
  "com.typesafe.akka" %% "akka-actor" % "2.3.14",
  "com.typesafe.akka" %% "akka-remote" % "2.3.14",
  "com.typesafe.play" %% "play-mailer" % "2.4.1",
  "org.postgresql" % "postgresql" % "9.4-1204-jdbc42"
)

includeFilter in (Assets, LessKeys.less) := "main.less"

excludeFilter in (Assets, LessKeys.less) := "_*.less"


fork in run := true