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
import scala.concurrent.ExecutionContext.Implicits.global
import doobie._
import doobie.implicits._
import eveapi.esi.model.Get_universe_system_kills_200_ok

object Resource {

  def build(schedulerStream: Stream[IO, Scheduler]): Stream[IO, HttpService[IO]] = {

    for {
      scheduler <- schedulerStream
      db        <- Stream.emit(Transactor.fromDriverManager[IO]("org.sqlite.JDBC", envOrNone("JDBC_URL").getOrElse("jdbc:sqlite:sqlite-latest.sqlite")))
      clients <- Stream.emit(IO { PooledHttp1Client[IO]() }.map { c =>
        (c, new EsiClient[IO]("ratting-radar-server", c.toHttpService))
      })
      topic <- Stream.eval(async.topic[IO, List[(Get_universe_system_kills_200_ok, SDE.SolarSystem)]](List.empty))
      input <- Stream.bracket(clients)(
        {
          case (_, esi) =>
            Stream.emit(
              scheduler
                .awakeEvery[IO](10.seconds)
                .evalMap { _ =>
                  for {
                    kills   <- universe.getUniverseSystemKills().run(esi).map(_.right.get)
                    systems <- SDE.getSystems.transact(db).map(_.groupByNel(_.solarSystemId))
                    joined  <- IO(kills.flatMap(x => systems.get(x.system_id.toLong).map(x -> _.head)))
                  } yield (joined)
                }
                .to(topic.publish))
        }, { case (http, _) => http.shutdown }
      )
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
        .concurrently(input)
    } yield (resource)
  }

}
