name := """smbvas-api"""
organization := "com.orangeade.smbvas"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.12.4"

libraryDependencies ++= Seq(
    jdbc,
    guice,
    ws,
    "org.playframework.anorm" %% "anorm" % "2.6.0",
    "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % Test,
    "org.postgresql" % "postgresql" % "42.2.1",
    "com.amazonaws" % "aws-java-sdk-s3" % "1.9.3"
)