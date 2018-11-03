name := """ieee-passau-advent-frontend"""
version := "2018-BASE"

scalaVersion := "2.11.8"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalacOptions in ThisBuild ++= Seq("-unchecked", "-deprecation", "-feature")

routesGenerator := InjectedRoutesGenerator

libraryDependencies ++= Seq(
  ws,
  filters,
  guice, 
  "com.typesafe.play" %% "play" % "2.6.20",
  "com.typesafe.play" %% "play-slick" % "3.0.1",
  "com.typesafe.play" %% "play-slick-evolutions" % "3.0.1",
  "com.typesafe.slick" %% "slick" % "3.2.3",
  "com.typesafe.play" %% "play-mailer" % "6.0.1",
  "com.typesafe.play" %% "play-mailer-guice" % "6.0.1",
  "org.postgresql" % "postgresql" % "42.2.5"
)

includeFilter in (Assets, LessKeys.less) := "main.less"
excludeFilter in (Assets, LessKeys.less) := "_*.less"

fork in run := true