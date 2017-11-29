organization := "space.inyour"
name := "ratting-radar-server"
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
  "org.tpolecat" %% "doobie-hikari"       % doobieVersion,
  "org.slf4j"    % "slf4j-simple"         % "1.7.25",
  "org.xerial"   % "sqlite-jdbc"          % "3.20.1"
)

enablePlugins(DockerPlugin)

dockerfile in docker := {
  val artifact: File     = assembly.value
  val artifactTargetPath = s"/app/${artifact.name}"
  val db: File           = (baseDirectory.value / "sqlite-latest.sqlite")

  new Dockerfile {
    from("openjdk:8-slim")
    add(artifact, artifactTargetPath)
    add(db, "/sqlite-latest.sqlite")
    env("HTTP_PORT", "8080")
    env("JDBC_URL", "jdbc:sqlite:/sqlite-latest.sqlite")
    env("ESI_USERAGENT", "ratting-radar-server")
    expose(8080)
    entryPoint("java", "-jar", artifactTargetPath)
  }
}

imageNames in docker := Seq(
  ImageName(s"andimiller/${name.value}:latest"),
  ImageName(s"andimiller/${name.value}:${version.value}")
)


import ReleaseTransformations._
releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,              // : ReleaseStep
  inquireVersions,                        // : ReleaseStep
  runClean,                               // : ReleaseStep
  runTest,                                // : ReleaseStep
  setReleaseVersion,                      // : ReleaseStep
  commitReleaseVersion,                   // : ReleaseStep, performs the initial git checks
  tagRelease,                             // : ReleaseStep
  docker,
  setNextVersion,                         // : ReleaseStep
  commitNextVersion,                      // : ReleaseStep
  pushChanges                             // : ReleaseStep, also checks that an upstream branch is properly configured
)
