package org.capnav.app.navigation

import org.capnav.app.model.GeoPoint
import org.capnav.app.model.Route
import org.capnav.app.model.RouteOptions
import org.capnav.app.model.Waypoint

data class Fix(
    val point: GeoPoint,
    val speedMps: Float,
    val bearingDeg: Float?,
    val accuracyM: Float,
    val timeMs: Long,
)

data class Progress(
    val matched: GeoPoint,
    val segmentIndex: Int,
    val maneuverIndex: Int,
    val distanceToManeuverM: Double,
    val remainingM: Double,
    val remainingS: Double,
    val legIndex: Int,
    val speedMps: Float,
) {
    companion object {
        fun initial(route: Route) = Progress(
            matched = route.shape.first(),
            segmentIndex = 0,
            maneuverIndex = if (route.maneuvers.size > 1) 1 else 0,
            distanceToManeuverM = route.maneuvers.firstOrNull()?.lengthM ?: 0.0,
            remainingM = route.lengthM,
            remainingS = route.durationS,
            legIndex = 0,
            speedMps = 0f,
        )
    }
}

/** Everything that must survive a process kill while a trip is in progress. */
data class ActiveTrip(
    val route: Route,
    /** Remaining waypoints; the last one is the destination. */
    val waypoints: List<Waypoint>,
    val options: RouteOptions,
    val progress: Progress,
    val startedAtMs: Long,
    val estimatedS: Double,
    val drivenM: Double,
) {
    val remainingWaypoints get() = waypoints.drop(progress.legIndex)
    val destination get() = waypoints.last()
}

data class TripSummary(
    val destinationName: String,
    val actualS: Double,
    val estimatedS: Double,
    val distanceM: Double,
    val waypoints: List<Waypoint>,
)

enum class StopReason { USER, ARRIVED }

enum class RerouteReason { DEVIATION, WAYPOINT_CHANGE, RESUME }

sealed interface TripState {
    data object Idle : TripState

    data class Previewing(
        val routes: List<Route>,
        val selected: Int,
        val origin: GeoPoint,
        val waypoints: List<Waypoint>,
        val options: RouteOptions,
        val loading: Boolean = false,
    ) : TripState {
        val selectedRoute get() = routes[selected]
    }

    data class Navigating(val trip: ActiveTrip) : TripState

    data class Paused(val trip: ActiveTrip, val pausedAtMs: Long) : TripState

    data class Rerouting(val trip: ActiveTrip, val reason: RerouteReason) : TripState

    data class Finished(val summary: TripSummary, val reachedDestination: Boolean) : TripState

    data class Error(val trip: ActiveTrip, val message: String) : TripState
}

val TripState.activeTrip: ActiveTrip?
    get() = when (this) {
        is TripState.Navigating -> trip
        is TripState.Paused -> trip
        is TripState.Rerouting -> trip
        is TripState.Error -> trip
        else -> null
    }

sealed interface TripEvent {
    data class Announce(val text: String) : TripEvent
    data class WaypointReached(val waypoint: Waypoint) : TripEvent
    data object Rerouted : TripEvent
    data class RerouteFailed(val message: String) : TripEvent
    data object Arrived : TripEvent
}

/** Persists the trip state so a pause (or an interrupted trip) survives a process kill. */
interface TripStore {
    suspend fun save(state: TripState)
    suspend fun load(): TripState?
}
