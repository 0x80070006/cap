package org.capnav.app.data.routing

import org.capnav.app.model.GeoPoint
import org.capnav.app.model.PoiCategory
import org.capnav.app.model.PoiWithDetour
import org.capnav.app.model.Route
import org.capnav.app.model.RouteRequest

interface RoutingRepository {
    /** Returns 1..3 alternatives, fastest first. */
    suspend fun computeRoutes(req: RouteRequest): Result<List<Route>>

    /** Points of interest near the remaining route, sorted by extra travel time. */
    suspend fun alongRoutePois(
        position: GeoPoint,
        remainingShape: List<GeoPoint>,
        category: PoiCategory,
        req: RouteRequest,
        maxDetourS: Int,
    ): Result<List<PoiWithDetour>>
}
