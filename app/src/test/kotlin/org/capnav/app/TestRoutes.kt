package org.capnav.app

import org.capnav.app.data.routing.RoutingRepository
import org.capnav.app.model.GeoPoint
import org.capnav.app.model.Maneuver
import org.capnav.app.model.ManeuverKind
import org.capnav.app.model.Place
import org.capnav.app.model.PoiCategory
import org.capnav.app.model.PoiWithDetour
import org.capnav.app.model.Route
import org.capnav.app.model.RouteRequest
import org.capnav.app.model.Waypoint
import org.capnav.app.navigation.Fix
import org.capnav.app.navigation.Geo

object TestRoutes {
    /** Straight eastward line at 48°N, one vertex every 0.001° (~74 m). */
    fun straight(from: GeoPoint, vertices: Int = 21): Route {
        val shape = (0 until vertices).map { GeoPoint(from.lat, from.lon + it * 0.001) }
        val len = Geo.cumulative(shape).last()
        val mid = vertices / 2
        return Route(
            shape = shape,
            maneuvers = listOf(
                Maneuver(ManeuverKind.DEPART, "Head east", null, "Head east", "Rue A", len / 2, 60.0, 0, 0),
                Maneuver(ManeuverKind.RIGHT, "Turn right", "Turn right onto Rue B", "Turn right now", "Rue B", len / 2, 60.0, mid, 0),
                Maneuver(ManeuverKind.ARRIVE, "Arrive", null, "You have arrived", null, 0.0, 0.0, vertices - 1, 0),
            ),
            lengthM = len,
            durationS = 120.0,
            hasToll = false,
            hasHighway = false,
            hasFerry = false,
            legEnds = listOf(vertices - 1),
        )
    }

    fun place(p: GeoPoint, name: String = "Dest") = Place(name, "", p)

    fun fix(p: GeoPoint, speed: Float = 10f, t: Long = 0) = Fix(p, speed, 90f, 5f, t)
}

class FakeRouting : RoutingRepository {
    val requests = mutableListOf<RouteRequest>()
    var fail = false

    override suspend fun computeRoutes(req: RouteRequest): Result<List<Route>> {
        requests += req
        if (fail) return Result.failure(IllegalStateException("offline"))
        return Result.success(listOf(TestRoutes.straight(req.origin)))
    }

    override suspend fun alongRoutePois(
        position: GeoPoint, remainingShape: List<GeoPoint>, category: PoiCategory, req: RouteRequest, maxDetourS: Int,
    ): Result<List<PoiWithDetour>> = Result.success(emptyList())
}

fun waypoint(id: Long, p: GeoPoint) = Waypoint(id, TestRoutes.place(p, "WP$id"))
