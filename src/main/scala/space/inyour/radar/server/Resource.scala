package space.inyour.radar.server

import eveapi.esi.client._, eveapi.esi.client.EsiClient._, eveapi.esi.api.CirceCodecs._
import org.http4s.client.blaze.PooledHttp1Client
import cats.effect._
import io.circe._, io.circe.syntax._, io.circe.generic.auto._, io.circe.java8.time._
import cats.syntax.either._, cats.syntax._, cats.implicits._
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
import doobie._, doobie.implicits._

class Resource(scheduler: Scheduler) {

  val httpClient = PooledHttp1Client[IO]()
  val esiClient  = new EsiClient[IO]("my esi client", httpClient.toHttpService)
  val xa = Transactor.fromDriverManager[IO](
    "org.sqlite.JDBC",
    envOrNone("JDBC_URL").getOrElse("jdbc:sqlite:sqlite-latest.sqlite")
  )

  val mainStream: Stream[IO, WebSocketFrame] = scheduler.awakeEvery[IO](10 seconds).evalMap { _ =>
    for {
      kills   <- universe.getUniverseSystemKills().run(esiClient).map(_.right.get)
      systems <- SDE.getSystems.transact(xa).map(_.groupByNel(_.solarSystemId))
      joined  <- IO(kills.flatMap(x => systems.get(x.system_id.toLong).map(x -> _.head)))
      json <- IO(
        joined.map {
          case (stats, system) =>
            Json.obj(
              "data" -> stats.asJson,
              "system" -> system.asJson
            )
        }
      )
    } yield Text(json.asJson.noSpaces)
  }

  def service(scheduler: Scheduler) = HttpService[IO] {
    case GET -> Root =>
      Ok("hi")
    case GET -> Root / "ws" =>
      val fromClient: Sink[IO, WebSocketFrame] = _.evalMap { (ws: WebSocketFrame) =>
        ws match {
          case Text(t, _) => IO { println(t) }
          case f          => IO { println("mysterious data came through") }
        }
      }
      WS(mainStream, fromClient)
  }

}
