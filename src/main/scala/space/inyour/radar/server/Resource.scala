package space.inyour.radar.server

import eveapi.esi.client._
import eveapi.esi.client.EsiClient._
import eveapi.esi.api.CirceCodecs._
import org.http4s.client.blaze.PooledHttp1Client
import cats.effect._
import io.circe.syntax._
import io.circe.generic.auto._
import cats.implicits._
import org.http4s.HttpService
import org.http4s.dsl._
import org.http4s.server.websocket._
import org.http4s.websocket.WebsocketBits._
import fs2._, fs2.{Stream, Scheduler}

import scala.concurrent.duration._
import scala.util.Properties.envOrNone
import doobie._
import doobie.hikari.HikariTransactor
import doobie.implicits._
import doobie.hikari.implicits._
import eveapi.esi.model.Get_universe_system_kills_200_ok

import scala.concurrent.ExecutionContext

object Resource {

  def build(port: Int, db: String)(implicit ec: ExecutionContext): Stream[IO, Nothing] = {
    Stream.bracket(
      for {
        scheduler  <- Scheduler.allocate[IO](4)
        httpClient <- IO { PooledHttp1Client[IO]() }
        xa         <- HikariTransactor[IO]("org.sqlite.JDBC", db, "", "")
      } yield (scheduler, httpClient, xa)
    )(
      {
        case ((scheduler, _), httpClient, xa) =>
          for {
            esiClient <- Stream.emit(new EsiClient[IO]("my esi client", httpClient.toHttpService))
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
            server <- BlazeBuilder[IO].bindHttp(port).withWebSockets(true).mountService(resource, "/").serve.concurrently(input)
          } yield server
      }, {
        case ((_, schedulerShutdown), httpClient, xa) =>
          for {
            _ <- schedulerShutdown
            _ <- httpClient.shutdown
            _ <- xa.shutdown
          } yield ()
      }
    )
  }

}
