package org.capnav.app.navigation

import org.capnav.app.data.TextStore
import org.capnav.app.data.places.PlacesRepository
import org.capnav.app.model.GeoPoint
import org.capnav.app.model.Maneuver
import org.capnav.app.model.ManeuverKind
import org.capnav.app.model.Route
import org.capnav.app.model.RouteLabel
import org.capnav.app.model.RouteOptions
import org.capnav.app.model.VehicleProfile
import org.capnav.app.model.Waypoint
import org.json.JSONArray
import org.json.JSONObject

/** Encrypted persistence of an in-progress trip. Idle/finished states wipe the file. */
class EncryptedTripStore(private val store: TextStore) : TripStore {
    override suspend fun save(state: TripState) {
        val (trip, paused) = when (state) {
            is TripState.Navigating -> state.trip to false
            is TripState.Rerouting -> state.trip to false
            is TripState.Error -> state.trip to false
            is TripState.Paused -> state.trip to true
            else -> null to false
        }
        if (trip == null) store.wipe() else store.write(TripCodec.encode(trip, paused))
    }

    override suspend fun load(): TripState? = store.read()?.let { text ->
        runCatching {
            val o = JSONObject(text)
            val trip = TripCodec.decodeTrip(o.getJSONObject("trip"))
            if (o.optBoolean("paused")) TripState.Paused(trip, o.optLong("pausedAt")) else TripState.Navigating(trip)
        }.getOrNull()
    }
}

object TripCodec {
    fun encode(trip: ActiveTrip, paused: Boolean): String =
        JSONObject().put("v", 1).put("paused", paused).put("pausedAt", System.currentTimeMillis())
            .put("trip", encodeTrip(trip)).toString()

    fun encodeTrip(t: ActiveTrip): JSONObject = JSONObject()
        .put("route", encodeRoute(t.route))
        .put("wps", JSONArray().apply {
            t.waypoints.forEach { put(JSONObject().put("id", it.id).put("p", PlacesRepository.placeToJson(it.place))) }
        })
        .put("opt", JSONObject().put("tolls", t.options.avoidTolls).put("hw", t.options.avoidHighways)
            .put("ferry", t.options.avoidFerries).put("unpaved", t.options.avoidUnpaved).put("veh", t.options.vehicle.name))
        .put("leg", t.progress.legIndex)
        .put("seg", t.progress.segmentIndex)
        .put("lat", t.progress.matched.lat).put("lon", t.progress.matched.lon)
        .put("remM", t.progress.remainingM).put("remS", t.progress.remainingS)
        .put("man", t.progress.maneuverIndex).put("toMan", t.progress.distanceToManeuverM)
        .put("start", t.startedAtMs)
        .put("est", t.estimatedS)
        .put("driven", t.drivenM)

    fun decodeTrip(o: JSONObject): ActiveTrip {
        val route = decodeRoute(o.getJSONObject("route"))
        val wpsArr = o.getJSONArray("wps")
        val wps = (0 until wpsArr.length()).map {
            val w = wpsArr.getJSONObject(it)
            Waypoint(w.getLong("id"), PlacesRepository.placeFromJson(w.getJSONObject("p")))
        }
        require(wps.isNotEmpty())
        val opt = o.getJSONObject("opt")
        val options = RouteOptions(
            avoidTolls = opt.optBoolean("tolls"),
            avoidHighways = opt.optBoolean("hw"),
            avoidFerries = opt.optBoolean("ferry"),
            avoidUnpaved = opt.optBoolean("unpaved"),
            vehicle = runCatching { VehicleProfile.valueOf(opt.getString("veh")) }.getOrDefault(VehicleProfile.CAR),
        )
        val progress = Progress.initial(route).copy(
            legIndex = o.optInt("leg").coerceIn(0, wps.lastIndex),
            segmentIndex = o.optInt("seg").coerceIn(0, (route.shape.size - 2).coerceAtLeast(0)),
            matched = GeoPoint(o.getDouble("lat"), o.getDouble("lon")),
            remainingM = o.optDouble("remM", route.lengthM),
            remainingS = o.optDouble("remS", route.durationS),
            maneuverIndex = o.optInt("man", if (route.maneuvers.size > 1) 1 else 0).coerceIn(0, (route.maneuvers.size - 1).coerceAtLeast(0)),
            distanceToManeuverM = o.optDouble("toMan", 0.0),
        )
        return ActiveTrip(route, wps, options, progress, o.getLong("start"), o.getDouble("est"), o.optDouble("driven", 0.0))
    }

    fun encodeRoute(r: Route): JSONObject = JSONObject()
        .put("shape", JSONArray().apply { r.shape.forEach { put(it.lat).put(it.lon) } })
        .put("man", JSONArray().apply {
            r.maneuvers.forEach { m ->
                put(JSONObject().put("k", m.kind.name).put("i", m.instruction).put("a", m.verbalAlert ?: "")
                    .put("p", m.verbalPre ?: "").put("s", m.street ?: "").put("l", m.lengthM).put("t", m.timeS)
                    .put("x", m.shapeIndex).put("g", m.legIndex))
            }
        })
        .put("len", r.lengthM).put("dur", r.durationS)
        .put("toll", r.hasToll).put("hw", r.hasHighway).put("ferry", r.hasFerry)
        .put("legs", JSONArray(r.legEnds))
        .put("label", r.label.name)

    fun decodeRoute(o: JSONObject): Route {
        val s = o.getJSONArray("shape")
        val shape = (0 until s.length() / 2).map { GeoPoint(s.getDouble(it * 2), s.getDouble(it * 2 + 1)) }
        val ma = o.getJSONArray("man")
        val maneuvers = (0 until ma.length()).map {
            val m = ma.getJSONObject(it)
            Maneuver(
                kind = runCatching { ManeuverKind.valueOf(m.getString("k")) }.getOrDefault(ManeuverKind.STRAIGHT),
                instruction = m.getString("i"),
                verbalAlert = m.optString("a").ifEmpty { null },
                verbalPre = m.optString("p").ifEmpty { null },
                street = m.optString("s").ifEmpty { null },
                lengthM = m.getDouble("l"),
                timeS = m.getDouble("t"),
                shapeIndex = m.getInt("x").coerceIn(0, shape.lastIndex),
                legIndex = m.getInt("g"),
            )
        }
        val legs = o.getJSONArray("legs")
        return Route(
            shape = shape,
            maneuvers = maneuvers,
            lengthM = o.getDouble("len"),
            durationS = o.getDouble("dur"),
            hasToll = o.optBoolean("toll"),
            hasHighway = o.optBoolean("hw"),
            hasFerry = o.optBoolean("ferry"),
            legEnds = (0 until legs.length()).map { legs.getInt(it) },
            label = runCatching { RouteLabel.valueOf(o.getString("label")) }.getOrDefault(RouteLabel.FASTEST),
        )
    }
}
