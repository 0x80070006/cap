package org.capnav.app.model

data class GeoPoint(val lat: Double, val lon: Double)

data class BoundingBox(val south: Double, val west: Double, val north: Double, val east: Double) {
    fun contains(p: GeoPoint) = p.lat in south..north && p.lon in west..east
}

data class Place(
    val name: String,
    val detail: String,
    val point: GeoPoint,
    val category: String? = null,
)

enum class VehicleProfile(val costing: String) {
    CAR("auto"), MOTORCYCLE("motorcycle"), TRUCK("truck"), BICYCLE("bicycle"), PEDESTRIAN("pedestrian")
}

data class RouteOptions(
    val avoidTolls: Boolean = false,
    val avoidHighways: Boolean = false,
    val avoidFerries: Boolean = false,
    val avoidUnpaved: Boolean = false,
    val vehicle: VehicleProfile = VehicleProfile.CAR,
)

data class Waypoint(val id: Long, val place: Place)

data class RouteRequest(
    val origin: GeoPoint,
    val waypoints: List<Waypoint>,
    val options: RouteOptions,
    val language: String,
)

/** Simplified Valhalla maneuver types we draw distinct arrows for. */
enum class ManeuverKind { DEPART, STRAIGHT, SLIGHT_RIGHT, RIGHT, SHARP_RIGHT, UTURN, SHARP_LEFT, LEFT, SLIGHT_LEFT, RAMP, MERGE, ROUNDABOUT, FERRY, WAYPOINT, ARRIVE }

data class Maneuver(
    val kind: ManeuverKind,
    val instruction: String,
    val verbalAlert: String?,
    val verbalPre: String?,
    val street: String?,
    val lengthM: Double,
    val timeS: Double,
    /** Index into [Route.shape] where this maneuver begins. */
    val shapeIndex: Int,
    val legIndex: Int,
)

data class Route(
    val shape: List<GeoPoint>,
    val maneuvers: List<Maneuver>,
    val lengthM: Double,
    val durationS: Double,
    val hasToll: Boolean,
    val hasHighway: Boolean,
    val hasFerry: Boolean,
    /** Shape index at which each leg ends (one per waypoint). */
    val legEnds: List<Int>,
    val label: RouteLabel = RouteLabel.FASTEST,
)

enum class RouteLabel { FASTEST, ALTERNATIVE }

/** The 12 categories of the "What do you see?" sheet plus complementary types. */
enum class AlertType(val subtypes: List<String>) {
    TRAFFIC_JAM(listOf("moderate", "heavy", "standstill")),
    POLICE(listOf("visible", "hidden", "my_side", "other_side")),
    ACCIDENT(listOf("minor", "major", "my_side", "other_side")),
    HAZARD(listOf("object", "pothole", "stopped_vehicle", "animal", "fog", "ice", "slippery", "lights_out")),
    ROAD_CLOSED(listOf("works", "accident", "event")),
    LANE_BLOCKED(listOf("left", "center", "right")),
    MAP_ISSUE(listOf("address", "missing_road", "wrong_oneway", "wrong_turn", "wrong_speed", "place_closed")),
    BAD_WEATHER(listOf("rain", "storm", "hail", "snow", "ice", "fog", "wind", "flood")),
    FUEL_PRICE(emptyList()),
    ROADSIDE_HELP(listOf("breakdown", "accident", "assistance")),
    MAP_NOTE(emptyList()),
    PLACE(emptyList()),
    ROADWORKS(emptyList()),
    SPEED_CAMERA(listOf("fixed", "mobile")),
}

@JvmInline
value class AlertId(val value: Long)

data class PersonalAlert(
    val id: AlertId,
    val type: AlertType,
    val subtype: String?,
    val point: GeoPoint,
    val createdAt: Long,
    val note: String?,
    /** Number of times the user reported/merged again at this place. */
    val passes: Int,
)

data class NewPersonalAlert(
    val type: AlertType,
    val point: GeoPoint,
    val subtype: String? = null,
    val note: String? = null,
)

sealed interface CreateAlertResult {
    data class Created(val alert: PersonalAlert) : CreateAlertResult
    data class Duplicate(val existing: PersonalAlert) : CreateAlertResult
}

data class PoiWithDetour(val place: Place, val detourS: Double)

enum class PoiCategory(val osmTag: String) {
    FUEL("osm.amenity.fuel"),
    CHARGING("osm.amenity.charging_station"),
    PARKING("osm.amenity.parking"),
    CAFE("osm.amenity.cafe"),
    TOILETS("osm.amenity.toilets"),
    PHARMACY("osm.amenity.pharmacy"),
    RESTAURANT("osm.amenity.restaurant"),
}
