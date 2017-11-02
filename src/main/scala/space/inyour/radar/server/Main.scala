package space.inyour.radar.server

import cats.effect.IO
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.util.StreamApp
import fs2._
import scala.util.Properties.envOrNone
import scala.concurrent.ExecutionContext.Implicits.global

object Main extends StreamApp[IO] {
  val port: Int = envOrNone("HTTP_PORT").fold(8080)(_.toInt)

  override def stream(args: List[String], requestShutdown: IO[Unit]): Stream[IO, Nothing] =
    Scheduler[IO](corePoolSize = 8).flatMap { scheduler =>
      val res = new Resource(scheduler)
      BlazeBuilder[IO]
        .bindHttp(8080)
        .withWebSockets(true)
        .mountService(res.service(scheduler), "/")
        .serve
    }

}
