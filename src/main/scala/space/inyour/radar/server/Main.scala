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
    for {
      scheduler <- Stream.eval(IO { Scheduler[IO](corePoolSize = 8) })
      resource  <- Resource.build(scheduler)
      server <- Scheduler[IO](corePoolSize = 8).flatMap { scheduler =>
        BlazeBuilder[IO]
          .bindHttp(port)
          .withWebSockets(true)
          .mountService(resource, "/")
          .serve
      }
    } yield (server)

}
