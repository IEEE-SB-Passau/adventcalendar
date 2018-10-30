name := """ieee-passau-advent-frontend"""
version := "2018-BASE"

scalaVersion := "2.11.8"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalacOptions in ThisBuild ++= Seq("-unchecked", "-deprecation", "-feature")

routesGenerator := InjectedRoutesGenerator

libraryDependencies ++= Seq(
  ws,
  cache,
  filters,
  "com.typesafe.play" %% "play" % "2.4.11",
  "com.typesafe.play" %% "play-slick" % "1.1.0",
  "com.typesafe.play" %% "play-slick-evolutions" % "1.1.0",
  "com.typesafe.slick" %% "slick" % "3.1.1",
  "com.typesafe.play" %% "anorm" % "2.4.0",
  "com.typesafe.akka" %% "akka-actor" % "2.3.14",
  "com.typesafe.akka" %% "akka-remote" % "2.3.14",
  "com.typesafe.play" %% "play-mailer" % "4.0.0",
  "org.postgresql" % "postgresql" % "42.2.5"
)

includeFilter in (Assets, LessKeys.less) := "main.less"
excludeFilter in (Assets, LessKeys.less) := "_*.less"

fork in run := true