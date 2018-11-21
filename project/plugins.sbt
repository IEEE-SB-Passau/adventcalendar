resolvers += Resolver.typesafeRepo("snapshots")
resolvers += Resolver.sonatypeRepo("snapshots")
resolvers += Resolver.sbtPluginRepo("snapshots")
resolvers += Resolver.typesafeIvyRepo("snapshots")

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.6.20")
addSbtPlugin("com.typesafe.sbt" % "sbt-less" % "1.1.2")
addSbtPlugin("com.typesafe.sbt" % "sbt-digest" % "1.1.4")
addSbtPlugin("com.typesafe.sbt" % "sbt-gzip" % "1.0.2")
