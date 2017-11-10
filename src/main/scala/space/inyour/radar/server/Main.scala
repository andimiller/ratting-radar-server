package space.inyour.radar.server

import cats.effect.IO
import org.http4s.util.StreamApp
import fs2.Stream
import scala.util.Properties.envOrNone
import scala.concurrent.ExecutionContext.Implicits.global

object Main extends StreamApp[IO] {
  val port: Int = envOrNone("HTTP_PORT").fold(8080)(_.toInt)
  val db: String = envOrNone("JDBC_URL").getOrElse("jdbc:sqlite:sqlite-latest.sqlite")
  val useragent: String = envOrNone("ESI_USERAGENT").getOrElse("default ratting-radar-server useragent")

  override def stream(args: List[String], requestShutdown: IO[Unit]): Stream[IO, Nothing] =
     Resource.build(port, db, useragent)
}
