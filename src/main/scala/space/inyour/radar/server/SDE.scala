package space.inyour.radar.server

import doobie.implicits._
import doobie._

object SDE {

  case class SolarSystem(
      solarSystemId: Long,
      solarSystemName: String,
      constellationID: Long,
      constellationName: String,
      regionID: Long,
      regionName: String,
      x: Double,
      y: Double,
      z: Double
  )

  def getSystems: ConnectionIO[List[SolarSystem]] =
    sql"""select
            ms.solarSystemID
           ,ms.solarSystemName
           ,ms.constellationID
           ,mc.constellationName
           ,ms.regionID
           ,mr.regionName
           ,ms.x
           ,ms.y
           ,ms.z
         from mapSolarSystems   ms
         join mapConstellations mc using (constellationID)
         join mapRegions        mr using (regionID)
      """.query[SolarSystem].list

}
