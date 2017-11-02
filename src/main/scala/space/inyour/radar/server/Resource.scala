package space.inyour.radar.server

import eveapi.esi.client._
import eveapi.esi.client.EsiClient._
import eveapi.esi.api.CirceCodecs._
import org.http4s.client.blaze.PooledHttp1Client
import cats.effect._
import io.circe._
import io.circe.syntax._
import io.circe.generic.auto._
import io.circe.java8.time._
import cats.syntax.either._
import cats.syntax._
import cats.implicits._
import org.http4s.HttpService
import org.http4s.dsl._
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.server.websocket._
import org.http4s.util.StreamApp
import org.http4s.websocket.WebsocketBits._
import fs2._

import scala.concurrent.duration._
import scala.util.Properties.envOrNone
import scala.concurrent.ExecutionContext.Implicits.global
import doobie._
import doobie.implicits._
import eveapi.esi.model.Get_universe_system_kills_200_ok

object Resource {

  def build(schedulerStream: Stream[IO, Scheduler]): Stream[IO, HttpService[IO]] = {

    for {
      scheduler  <- schedulerStream
      httpClient <- Stream.emit(PooledHttp1Client[IO]())
      esiClient  <- Stream.emit(new EsiClient[IO]("my esi client", httpClient.toHttpService))
      db         <- Stream.emit(Transactor.fromDriverManager[IO]("org.sqlite.JDBC", envOrNone("JDBC_URL").getOrElse("jdbc:sqlite:sqlite-latest.sqlite")))
      topic      <- Stream.eval(async.topic[IO, List[(Get_universe_system_kills_200_ok, SDE.SolarSystem)]](List.empty))
      input <- Stream.emit(
        scheduler
          .awakeEvery[IO](10 seconds)
          .evalMap { _ =>
            for {
              kills   <- universe.getUniverseSystemKills().run(esiClient).map(_.right.get)
              systems <- SDE.getSystems.transact(db).map(_.groupByNel(_.solarSystemId))
              joined  <- IO(kills.flatMap(x => systems.get(x.system_id.toLong).map(x -> _.head)))
            } yield (joined)
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
        .concurrently(input)
    } yield (resource)
  }

}
