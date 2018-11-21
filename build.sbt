name := """ieee-passau-advent-frontend"""
version := "2018-BASE"

scalaVersion := "2.12.7"

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .enablePlugins(SbtWeb)

pipelineStages := Seq(digest, gzip)
LessKeys.compress in Assets := true
includeFilter in gzip := "*.html" || "*.css" || "*.js" || "*.jpg"

scalacOptions in ThisBuild ++= Seq("-unchecked", "-deprecation", "-feature")

routesGenerator := InjectedRoutesGenerator

libraryDependencies ++= Seq(
  ws,
  filters,
  guice,
  "com.typesafe.play" %% "play" % "2.6.20",
  "com.typesafe.play" %% "play-slick" % "3.0.3",
  "com.typesafe.play" %% "play-slick-evolutions" % "3.0.3",
  "com.typesafe.slick" %% "slick" % "3.2.3",
  "com.typesafe.play" %% "play-mailer" % "6.0.1",
  "com.typesafe.play" %% "play-mailer-guice" % "6.0.1",
  "org.postgresql" % "postgresql" % "42.2.5",
  "commons-io" % "commons-io" % "2.6"
)

includeFilter in (Assets, LessKeys.less) := "main.less"
excludeFilter in (Assets, LessKeys.less) := "_*.less"

fork in run := true
