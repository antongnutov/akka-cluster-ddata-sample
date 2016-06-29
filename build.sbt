name := "akka-cluster-ddata-sample"

version := "0.1"

organization in ThisBuild := "sample"

scalaVersion := "2.11.8"

val akkaVersion = "2.4.7"
val log4j2Version = "2.5"

libraryDependencies ++= Seq(
    // Akka
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
    "com.typesafe.akka" %% "akka-distributed-data-experimental" % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,

    // Logging
    "org.apache.logging.log4j" % "log4j-slf4j-impl" % log4j2Version,
    "org.apache.logging.log4j" % "log4j-core" % log4j2Version,
    "org.apache.logging.log4j" % "log4j-api" % log4j2Version,
    "com.lmax" % "disruptor" % "3.3.2",

    // Test
    "org.scalatest" %% "scalatest" % "3.0.0-RC3" % "test",
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test"
  )

enablePlugins(JavaAppPackaging)

// Bash Script config
bashScriptExtraDefines += """addJava "-Dconfig.file=${app_home}/../conf/app.conf""""
bashScriptExtraDefines += """addJava "-Dlog4j.configurationFile=${app_home}/../conf/log4j2.xml""""

bashScriptExtraDefines += """addJava "-Dcom.sun.management.jmxremote.port=1099""""
bashScriptExtraDefines += """addJava "-Dcom.sun.management.jmxremote.authenticate=false""""
bashScriptExtraDefines += """addJava "-Dcom.sun.management.jmxremote.ssl=false""""