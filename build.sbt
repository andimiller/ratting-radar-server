organization := "space.inyour"
name := "ratting-radar-server"
version := "0.0.1-SNAPSHOT"
scalaVersion := "2.11.8"

resolvers += Resolver.jcenterRepo
resolvers += Resolver.sonatypeRepo("release")

val esiClientVersion = "1.836.0"
val http4sVersion    = "0.18.0-M1"
val doobieVersion    = "0.5.0-M8"

libraryDependencies ++= Seq(
  "eveapi"       %% "esi-client"          % esiClientVersion,
  "org.http4s"   %% "http4s-blaze-server" % http4sVersion,
  "org.tpolecat" %% "doobie-core"         % doobieVersion,
  "org.xerial"   % "sqlite-jdbc"          % "3.20.1",
  "org.slf4j"    % "slf4j-simple"         % "1.7.25"
)
