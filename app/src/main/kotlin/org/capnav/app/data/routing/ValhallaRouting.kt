package org.capnav.app.data.routing

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.capnav.app.data.geocoding.GeocodingRepository
import org.capnav.app.data.network.HttpClients.getString
import org.capnav.app.model.GeoPoint
import org.capnav.app.model.Maneuver
import org.capnav.app.model.ManeuverKind
import org.capnav.app.model.PoiCategory
import org.capnav.app.model.PoiWithDetour
import org.capnav.app.model.Route
import org.capnav.app.model.RouteLabel
import org.capnav.app.model.RouteOptions
import org.capnav.app.model.RouteRequest
import org.capnav.app.model.VehicleProfile
import org.capnav.app.navigation.Geo
import org.json.JSONArray
import org.json.JSONObject

/** Valhalla HTTP client (ADR-003). The base URL is user-configurable to point at a self-hosted server. */
class ValhallaRouting(
    private val http: OkHttpClient,
    private val baseUrl: () -> String,
    private val geocoding: GeocodingRepository,
) : RoutingRepository {

    override suspend fun computeRoutes(req: RouteRequest): Result<List<Route>> = runCatching {
        val body = buildRouteJson(req)
        val text = http.getString(endpoint("route", body))
        parseRouteResponse(text)
    }

    override suspend fun alongRoutePois(
        position: GeoPoint,
        remainingShape: List<GeoPoint>,
        category: PoiCategory,
        req: RouteRequest,
        maxDetourS: Int,
    ): Result<List<PoiWithDetour>> = runCatching {
        val target = req.waypoints.first().place.point
        val cum = Geo.cumulative(remainingShape)
        val total = cum.lastOrNull() ?: 0.0
        // Sample a few points ahead so results stay near the road actually being driven.
        val samples = listOf(1_500.0, 6_000.0, 15_000.0).filter { it < total }.ifEmpty { listOf(total / 2) }
            .map { Geo.along(remainingShape, cum, it) }
        val candidates = samples.flatMap { geocoding.category(category, it, limit = 6).getOrDefault(emptyList()) }
            .distinctBy { it.point }
            .take(MAX_MATRIX_POIS)
        if (candidates.isEmpty()) return@runCatching emptyList()

        val locs = { pts: List<GeoPoint> -> JSONArray().apply { pts.forEach { put(loc(it)) } } }
        val json = JSONObject()
            .put("sources", locs(listOf(position) + candidates.map { it.point }))
            .put("targets", locs(candidates.map { it.point } + target))
            .put("costing", req.options.vehicle.costing)
        val matrix = JSONObject(http.getString(endpoint("sources_to_targets", json))).getJSONArray("sources_to_targets")
        val n = candidates.size
        val direct = matrix.getJSONArray(0).getJSONObject(n).optDouble("time", Double.NaN)
        candidates.mapIndexedNotNull { i, place ->
            val toPoi = matrix.getJSONArray(0).getJSONObject(i).optDouble("time", Double.NaN)
            val poiToTarget = matrix.getJSONArray(i + 1).getJSONObject(n).optDouble("time", Double.NaN)
            if (toPoi.isNaN() || poiToTarget.isNaN() || direct.isNaN()) return@mapIndexedNotNull null
            val detour = (toPoi + poiToTarget - direct).coerceAtLeast(0.0)
            if (detour <= maxDetourS) PoiWithDetour(place, detour) else null
        }.sortedBy { it.detourS }
    }

    private fun endpoint(action: String, json: JSONObject): HttpUrl =
        baseUrl().trimEnd('/').toHttpUrl().newBuilder()
            .addPathSegment(action)
            .addQueryParameter("json", json.toString())
            .build()

    companion object {
        private const val MAX_MATRIX_POIS = 12

        private fun loc(p: GeoPoint, type: String = "break") =
            JSONObject().put("lat", round6(p.lat)).put("lon", round6(p.lon)).put("type", type)

        private fun round6(v: Double) = Math.round(v * 1e6) / 1e6

        fun buildRouteJson(req: RouteRequest): JSONObject {
            val locations = JSONArray().put(loc(req.origin))
            req.waypoints.forEach { locations.put(loc(it.place.point)) }
            val o = req.options
            val costing = req.options.vehicle.costing
            val costingOptions = JSONObject().put(costing, costingOptionsFor(o))
            return JSONObject()
                .put("locations", locations)
                .put("costing", costing)
                .put("costing_options", costingOptions)
                .put("units", "kilometers")
                .put("language", req.language)
                .put("directions_options", JSONObject().put("units", "kilometers").put("language", req.language))
                .apply { if (req.waypoints.size == 1) put("alternates", 2) }
        }

        private fun costingOptionsFor(o: RouteOptions) = JSONObject().apply {
            when (o.vehicle) {
                VehicleProfile.CAR, VehicleProfile.MOTORCYCLE, VehicleProfile.TRUCK -> {
                    put("use_tolls", if (o.avoidTolls) 0.0 else 0.5)
                    put("use_highways", if (o.avoidHighways) 0.0 else 1.0)
                    put("use_ferry", if (o.avoidFerries) 0.0 else 0.5)
                    if (o.avoidUnpaved) put("exclude_unpaved", true)
                }
                VehicleProfile.BICYCLE -> put("use_ferry", if (o.avoidFerries) 0.0 else 0.5)
                VehicleProfile.PEDESTRIAN -> put("use_ferry", if (o.avoidFerries) 0.0 else 0.5)
            }
        }

        fun parseRouteResponse(text: String): List<Route> {
            val root = JSONObject(text)
            val main = parseTrip(root.getJSONObject("trip"), RouteLabel.FASTEST)
            val alts = root.optJSONArray("alternates")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    arr.optJSONObject(i)?.optJSONObject("trip")?.let { parseTrip(it, RouteLabel.ALTERNATIVE) }
                }
            }.orEmpty()
            return (listOf(main) + alts).take(3)
        }

        private fun parseTrip(trip: JSONObject, label: RouteLabel): Route {
            val legs = trip.getJSONArray("legs")
            val shape = ArrayList<GeoPoint>()
            val maneuvers = ArrayList<Maneuver>()
            val legEnds = ArrayList<Int>()
            for (l in 0 until legs.length()) {
                val leg = legs.getJSONObject(l)
                val legShape = Geo.decodePolyline6(leg.getString("shape"))
                val offset = if (shape.isEmpty()) 0 else shape.size - 1
                if (shape.isEmpty()) shape.addAll(legShape) else shape.addAll(legShape.drop(1))
                legEnds += shape.size - 1
                val ms = leg.getJSONArray("maneuvers")
                for (i in 0 until ms.length()) {
                    val m = ms.getJSONObject(i)
                    val type = m.optInt("type")
                    val last = i == ms.length() - 1
                    maneuvers += Maneuver(
                        kind = kindOf(type, isFinalLeg = l == legs.length() - 1 && last),
                        instruction = m.optString("instruction"),
                        verbalAlert = m.optString("verbal_transition_alert_instruction").ifEmpty { null },
                        verbalPre = m.optString("verbal_pre_transition_instruction").ifEmpty { null },
                        street = m.optJSONArray("street_names")?.optString(0)?.ifEmpty { null }
                            ?: m.optJSONArray("begin_street_names")?.optString(0)?.ifEmpty { null },
                        lengthM = m.optDouble("length", 0.0) * 1000.0,
                        timeS = m.optDouble("time", 0.0),
                        shapeIndex = (offset + m.optInt("begin_shape_index")).coerceAtMost(shape.size - 1),
                        legIndex = l,
                    )
                }
            }
            val summary = trip.getJSONObject("summary")
            return Route(
                shape = shape,
                maneuvers = maneuvers,
                lengthM = summary.optDouble("length", 0.0) * 1000.0,
                durationS = summary.optDouble("time", 0.0),
                hasToll = summary.optBoolean("has_toll"),
                hasHighway = summary.optBoolean("has_highway"),
                hasFerry = summary.optBoolean("has_ferry"),
                legEnds = legEnds,
                label = label,
            )
        }

        fun kindOf(type: Int, isFinalLeg: Boolean): ManeuverKind = when (type) {
            1, 2, 3 -> ManeuverKind.DEPART
            4, 5, 6 -> if (isFinalLeg) ManeuverKind.ARRIVE else ManeuverKind.WAYPOINT
            9, 23 -> ManeuverKind.SLIGHT_RIGHT
            10, 20 -> ManeuverKind.RIGHT
            11 -> ManeuverKind.SHARP_RIGHT
            12, 13 -> ManeuverKind.UTURN
            14 -> ManeuverKind.SHARP_LEFT
            15, 21 -> ManeuverKind.LEFT
            16, 24 -> ManeuverKind.SLIGHT_LEFT
            17, 18, 19 -> ManeuverKind.RAMP
            25, 37, 38 -> ManeuverKind.MERGE
            26, 27 -> ManeuverKind.ROUNDABOUT
            28, 29 -> ManeuverKind.FERRY
            else -> ManeuverKind.STRAIGHT
        }
    }
}
