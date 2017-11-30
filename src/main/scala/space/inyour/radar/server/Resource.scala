package space.inyour.radar.server

// http4s
import org.http4s.client.blaze.PooledHttp1Client
import org.http4s.HttpService
import org.http4s.dsl._
import org.http4s.server.websocket._
import org.http4s.websocket.WebsocketBits._
import org.http4s.client.Client
import org.http4s.server.blaze.BlazeBuilder
import org.slf4j.LoggerFactory
// circe
import io.circe.syntax._
import io.circe.generic.auto._
// esi-client
import eveapi.esi.client._
import eveapi.esi.client.EsiClient._
import eveapi.esi.api.CirceCodecs._
import eveapi.esi.model.Get_universe_system_kills_200_ok
// fs2
import fs2._
// cats
import cats.implicits._
import cats.effect._
// doobie
import doobie.hikari.HikariTransactor
import doobie.implicits._
import doobie.hikari.implicits._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object Resource {

  val log = LoggerFactory.getLogger(getClass)

  def build(port: Int, db: String, useragent: String)(implicit ec: ExecutionContext): Stream[IO, Nothing] = {
    log.info("starting up")
    Stream.bracket[IO, ((Scheduler, IO[Unit]), Client[IO], HikariTransactor[IO]), Nothing](
      for {
        scheduler  <- Scheduler.allocate[IO](4)
        httpClient <- IO { PooledHttp1Client[IO]() }
        xa         <- HikariTransactor[IO]("org.sqlite.JDBC", db, "", "")
      } yield (scheduler, httpClient, xa)
    )(
      {
        case ((scheduler, _), httpClient, xa) =>
          for {
            esiClient <- Stream.emit(new EsiClient[IO](useragent, httpClient.toHttpService))
            topic     <- Stream.eval(async.topic[IO, List[(Get_universe_system_kills_200_ok, SDE.SolarSystem)]](List.empty))
            input <- Stream.emit(
              scheduler
                .awakeEvery[IO](10 seconds)
                .evalMap { _ =>
                  for {
                    kills   <- universe.getUniverseSystemKills().run(esiClient).map(_.right.get)
                    systems <- SDE.getSystems.transact(xa).map(_.groupByNel(_.solarSystemId))
                    joined  <- IO(kills.flatMap(x => systems.get(x.system_id.toLong).map(x -> _.head)))
                  } yield joined
                }
                .to(topic.publish))
            resource <- Stream
              .eval(IO {
                HttpService[IO] {
                  case GET -> Root =>
                    Ok("hi")
                  case GET -> Root / "ws" =>
                    val fromClient: Sink[IO, WebSocketFrame] = Sink[IO, WebSocketFrame](_ => IO.pure(()))
                    WS(topic.subscribe(10).map(x => Text(x.asJson.noSpaces)), fromClient)
                }
              })
            server <- BlazeBuilder[IO].bindHttp(port, "0.0.0.0").withWebSockets(true).mountService(resource, "/").serve.concurrently(input).asInstanceOf[Stream[IO, Nothing]]
          } yield server
      }, {
        case ((_, schedulerShutdown), httpClient, xa) =>
          for {
            _ <- schedulerShutdown
            _ <- httpClient.shutdown
            _ <- xa.shutdown
            _ <- IO { log.info("gracefully shutting down") }
          } yield ()
      }
    )
  }

}
