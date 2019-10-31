name := """ieee-passau-advent-frontend"""
version := "2019-BASE"

scalaVersion := "2.12.10"

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .enablePlugins(SbtWeb)

pipelineStages := Seq(digest, gzip)
LessKeys.compress in Assets := true
includeFilter in gzip := "*.html" || "*.css" || "*.js" || "*.ico"

scalacOptions in ThisBuild ++= Seq("-unchecked", "-deprecation", "-feature")

routesGenerator := InjectedRoutesGenerator

libraryDependencies ++= Seq(
  ws,
  filters,
  guice,
  "com.typesafe.play" %% "play" % "2.7.3",
  "com.typesafe.play" %% "play-slick" % "4.0.2",
  "com.typesafe.play" %% "play-slick-evolutions" % "4.0.2",
  "com.typesafe.slick" %% "slick" % "3.3.2",
  "com.typesafe.play" %% "play-mailer" % "7.0.1",
  "com.typesafe.play" %% "play-mailer-guice" % "7.0.1",
  "org.postgresql" % "postgresql" % "42.2.8",
  "commons-io" % "commons-io" % "2.6",
  "commons-codec" % "commons-codec" % "1.13"
)

includeFilter in (Assets, LessKeys.less) := "main.less"
excludeFilter in (Assets, LessKeys.less) := "_*.less"

fork in run := true
