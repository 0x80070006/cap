package org.capnav.app.navigation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.capnav.app.data.routing.RoutingRepository
import org.capnav.app.model.GeoPoint
import org.capnav.app.model.Route
import org.capnav.app.model.RouteOptions
import org.capnav.app.model.RouteRequest
import org.capnav.app.model.Waypoint
import kotlin.math.max

fun interface AnnouncementFormatter {
    fun ahead(distanceM: Double, instruction: String): String
}

/**
 * Trip state machine (docs/ARCHITECTURE.md §TripState). All transitions go through [set], which
 * persists the new state and appends a PII-free entry to [transitions].
 */
class TripEngine(
    private val routing: RoutingRepository,
    private val store: TripStore,
    scope: CoroutineScope,
    private val clock: () -> Long,
    private val language: () -> String,
    private val formatter: AnnouncementFormatter = AnnouncementFormatter { d, s -> "${d.toInt()} m: $s" },
) {
    private val saves = Channel<TripState>(Channel.CONFLATED)

    init {
        scope.launch { for (s in saves) store.save(s) }
    }

    private val _state = MutableStateFlow<TripState>(TripState.Idle)
    val state: StateFlow<TripState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<TripEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<TripEvent> = _events.asSharedFlow()

    private val _transitions = ArrayDeque<String>()
    val transitions: List<String> get() = synchronized(_transitions) { _transitions.toList() }

    private val mutex = Mutex()
    private var cumulative: DoubleArray = DoubleArray(0)
    private var cumulativeFor: Route? = null
    private var offRouteCount = 0
    private var lastFix: Fix? = null
    private val announced = HashSet<Long>()
    private var lastPeriodicSaveMs = 0L
    private var lastRerouteFailureMs = 0L
    private var generation = 0L

    suspend fun restore() = mutex.withLock {
        val restored = when (val s = store.load()) {
            null, is TripState.Previewing, is TripState.Finished -> TripState.Idle
            is TripState.Navigating -> TripState.Paused(s.trip, clock())
            is TripState.Rerouting -> TripState.Paused(s.trip, clock())
            is TripState.Error -> TripState.Paused(s.trip, clock())
            else -> s
        }
        set(restored)
    }

    suspend fun preview(routes: List<Route>, origin: GeoPoint, waypoints: List<Waypoint>, options: RouteOptions) =
        mutex.withLock {
            require(routes.isNotEmpty()) { "preview needs at least one route" }
            require(waypoints.isNotEmpty()) { "preview needs a destination" }
            set(TripState.Previewing(routes, 0, origin, waypoints, options))
        }

    suspend fun select(index: Int) = mutex.withLock {
        val s = _state.value as? TripState.Previewing ?: return@withLock
        if (index in s.routes.indices) set(s.copy(selected = index))
    }

    suspend fun cancelPreview() = mutex.withLock {
        if (_state.value is TripState.Previewing) set(TripState.Idle)
    }

    suspend fun start() = mutex.withLock {
        val s = _state.value as? TripState.Previewing ?: return@withLock
        val route = s.selectedRoute
        val trip = ActiveTrip(route, s.waypoints, s.options, Progress.initial(route), clock(), route.durationS, 0.0)
        resetTracking()
        set(TripState.Navigating(trip))
        route.maneuvers.firstOrNull()?.let { m -> emit(TripEvent.Announce(m.verbalPre ?: m.instruction)) }
    }

    suspend fun onLocation(fix: Fix) {
        var rerouteNeeded = false
        mutex.withLock {
            val s = _state.value
            val trip = s.activeTrip ?: return@withLock
            val previous = lastFix
            lastFix = fix
            val driven = trip.drivenM + if (previous != null) {
                Geo.distanceM(previous.point, fix.point).takeIf { it < 500 } ?: 0.0
            } else 0.0
            if (s !is TripState.Navigating && s !is TripState.Error) {
                if (s is TripState.Rerouting) set(s.copy(trip = trip.copy(drivenM = driven)), persist = false)
                return@withLock
            }
            val updated = track(trip.copy(drivenM = driven), fix)
            when {
                updated == null -> {
                    if (s is TripState.Navigating || clock() - lastRerouteFailureMs > REROUTE_RETRY_MS) {
                        set(TripState.Rerouting(trip.copy(drivenM = driven), RerouteReason.DEVIATION))
                        rerouteNeeded = true
                    }
                }
                isArrived(updated, fix) -> finish(updated, reached = true)
                else -> {
                    val persist = clock() - lastPeriodicSaveMs > PERIODIC_SAVE_MS
                    if (persist) lastPeriodicSaveMs = clock()
                    set(TripState.Navigating(updated), persist = persist || s is TripState.Error)
                }
            }
        }
        if (rerouteNeeded) reroute()
    }

    suspend fun pause() = mutex.withLock {
        val trip = _state.value.activeTrip ?: return@withLock
        if (_state.value is TripState.Paused) return@withLock
        generation++
        set(TripState.Paused(trip, clock()))
    }

    /** Resumes and recomputes from the current position, since the user may have moved while paused. */
    suspend fun resume() {
        mutex.withLock {
            val s = _state.value
            val trip = when (s) {
                is TripState.Paused -> s.trip
                is TripState.Error -> s.trip
                else -> return
            }
            set(TripState.Rerouting(trip, RerouteReason.RESUME))
        }
        reroute()
    }

    suspend fun stop(reason: StopReason) = mutex.withLock {
        val trip = _state.value.activeTrip ?: return@withLock
        generation++
        finish(trip, reached = reason == StopReason.ARRIVED)
    }

    suspend fun dismissSummary() = mutex.withLock {
        if (_state.value is TripState.Finished) set(TripState.Idle)
    }

    /** Inserts a stop before the destination unless [index] (in remaining waypoints) is given. */
    suspend fun addWaypoint(wp: Waypoint, index: Int? = null) = editWaypoints { list ->
        val at = (index ?: (list.size - 1)).coerceIn(0, list.size)
        list.toMutableList().apply { add(at, wp) }
    }

    suspend fun removeWaypoint(id: Long) = editWaypoints { list ->
        if (list.size <= 1) list else list.filterNot { it.id == id }
    }

    suspend fun skipWaypoint() = editWaypoints { list -> if (list.size <= 1) list else list.drop(1) }

    suspend fun reorderWaypoints(order: List<Long>) = editWaypoints { list ->
        val byId = list.associateBy { it.id }
        val reordered = order.mapNotNull { byId[it] }
        if (reordered.size == list.size) reordered else list
    }

    private suspend fun editWaypoints(transform: (List<Waypoint>) -> List<Waypoint>) {
        val s = _state.value
        when (s) {
            is TripState.Previewing -> {
                val newList = transform(s.waypoints)
                if (newList == s.waypoints) return
                mutex.withLock { set(s.copy(waypoints = newList, loading = true), persist = false) }
                val result = routing.computeRoutes(RouteRequest(s.origin, newList, s.options, language()))
                mutex.withLock {
                    val cur = _state.value as? TripState.Previewing ?: return
                    result.onSuccess { routes ->
                        if (routes.isNotEmpty()) set(TripState.Previewing(routes, 0, s.origin, newList, s.options))
                    }.onFailure {
                        set(cur.copy(waypoints = s.waypoints, loading = false), persist = false)
                        emit(TripEvent.RerouteFailed(it.message ?: "routing failed"))
                    }
                }
            }
            is TripState.Navigating, is TripState.Paused, is TripState.Error -> {
                val trip = s.activeTrip!!
                val remaining = transform(trip.remainingWaypoints)
                if (remaining == trip.remainingWaypoints) return
                mutex.withLock {
                    val newTrip = trip.copy(waypoints = remaining, progress = trip.progress.copy(legIndex = 0))
                    set(TripState.Rerouting(newTrip, RerouteReason.WAYPOINT_CHANGE))
                }
                reroute()
            }
            else -> Unit
        }
    }

    private suspend fun reroute() {
        val (trip, gen, origin) = mutex.withLock {
            val s = _state.value as? TripState.Rerouting ?: return
            val origin = lastFix?.point ?: s.trip.progress.matched
            Triple(s.trip, ++generation, origin)
        }
        val req = RouteRequest(origin, trip.remainingWaypoints, trip.options, language())
        val result = routing.computeRoutes(req)
        mutex.withLock {
            val s = _state.value as? TripState.Rerouting ?: return
            if (gen != generation) return
            result.fold(
                onSuccess = { routes ->
                    val route = routes.firstOrNull()
                    if (route == null) {
                        failReroute(s.trip, "no route")
                    } else {
                        resetTracking()
                        val newTrip = s.trip.copy(
                            route = route,
                            waypoints = s.trip.remainingWaypoints,
                            progress = Progress.initial(route),
                        )
                        set(TripState.Navigating(newTrip))
                        emit(TripEvent.Rerouted)
                    }
                },
                onFailure = { failReroute(s.trip, it.message ?: "routing failed") },
            )
        }
    }

    private fun failReroute(trip: ActiveTrip, message: String) {
        lastRerouteFailureMs = clock()
        set(TripState.Error(trip, message))
        emit(TripEvent.RerouteFailed(message))
    }

    /** Map-matches [fix] onto the route. Returns null once deviation is confirmed (hysteresis). */
    private fun track(trip: ActiveTrip, fix: Fix): ActiveTrip? {
        val route = trip.route
        val cum = cumulativeOf(route)
        val p = trip.progress
        val from = max(0, p.segmentIndex - 2)
        val to = minOf(route.shape.size - 1, p.segmentIndex + LOOKAHEAD_SEGMENTS)
        val proj = Geo.project(fix.point, route.shape, from, to)
        val threshold = max(OFF_ROUTE_MIN_M, fix.accuracyM * 1.5)
        if (proj == null || proj.distanceM > threshold) {
            offRouteCount++
            return if (offRouteCount >= OFF_ROUTE_CONFIRMATIONS) null
            else trip.copy(progress = p.copy(speedMps = fix.speedMps))
        }
        offRouteCount = 0
        val seg = proj.segmentIndex
        val along = cum[seg] + proj.t * (cum[seg + 1] - cum[seg])
        val total = cum.last()
        val maneuvers = route.maneuvers
        var next = maneuvers.indexOfFirst { cum[it.shapeIndex.coerceAtMost(cum.size - 1)] > along + 1.0 }
        if (next == -1) next = maneuvers.lastIndex
        val nextStart = cum[maneuvers[next].shapeIndex.coerceAtMost(cum.size - 1)]
        val distToManeuver = max(0.0, nextStart - along)
        val currentIdx = max(0, next - 1)
        val curStart = cum[maneuvers[currentIdx].shapeIndex.coerceAtMost(cum.size - 1)]
        val curLen = nextStart - curStart
        val frac = if (curLen <= 0.0) 0.0 else (distToManeuver / curLen).coerceIn(0.0, 1.0)
        var remainingS = frac * maneuvers[currentIdx].timeS
        for (i in next until maneuvers.size) remainingS += maneuvers[i].timeS
        val leg = route.legEnds.indexOfFirst { it > seg }.let { if (it == -1) route.legEnds.lastIndex else it }

        val newProgress = Progress(
            matched = proj.point,
            segmentIndex = seg,
            maneuverIndex = next,
            distanceToManeuverM = distToManeuver,
            remainingM = max(0.0, total - along),
            remainingS = remainingS,
            legIndex = max(leg, p.legIndex),
            speedMps = fix.speedMps,
        )
        if (newProgress.legIndex > p.legIndex) {
            for (i in p.legIndex until newProgress.legIndex) trip.waypoints.getOrNull(i)?.let {
                emit(TripEvent.WaypointReached(it))
            }
        }
        announce(maneuvers[next], next, distToManeuver, fix.speedMps)
        return trip.copy(progress = newProgress)
    }

    private fun announce(m: org.capnav.app.model.Maneuver, index: Int, dist: Double, speed: Float) {
        val v = max(speed.toDouble(), 8.0)
        val far = max(300.0, v * 25)
        val near = max(50.0, v * 7)
        val farKey = index.toLong() shl 1
        val nearKey = farKey or 1
        if (dist <= near && nearKey !in announced) {
            announced += nearKey
            announced += farKey
            emit(TripEvent.Announce(m.verbalPre ?: m.instruction))
        } else if (dist <= far && dist > near * 1.5 && farKey !in announced) {
            announced += farKey
            emit(TripEvent.Announce(formatter.ahead(dist, m.verbalAlert ?: m.instruction)))
        }
    }

    private fun isArrived(trip: ActiveTrip, fix: Fix): Boolean =
        trip.progress.legIndex >= trip.waypoints.lastIndex &&
            (Geo.distanceM(fix.point, trip.destination.place.point) < ARRIVAL_RADIUS_M ||
                trip.progress.remainingM < ARRIVAL_REMAINING_M)

    private fun finish(trip: ActiveTrip, reached: Boolean) {
        val summary = TripSummary(
            destinationName = trip.destination.place.name,
            actualS = (clock() - trip.startedAtMs) / 1000.0,
            estimatedS = trip.estimatedS,
            distanceM = trip.drivenM,
            waypoints = trip.waypoints,
        )
        set(TripState.Finished(summary, reached))
        if (reached) emit(TripEvent.Arrived)
    }

    private fun cumulativeOf(route: Route): DoubleArray {
        if (cumulativeFor !== route) {
            cumulative = Geo.cumulative(route.shape)
            cumulativeFor = route
        }
        return cumulative
    }

    private fun resetTracking() {
        offRouteCount = 0
        announced.clear()
        cumulativeFor = null
    }

    private fun emit(e: TripEvent) {
        _events.tryEmit(e)
    }

    private fun set(newState: TripState, persist: Boolean = true) {
        val old = _state.value
        _state.value = newState
        if (old::class != newState::class) {
            synchronized(_transitions) {
                _transitions.addLast("${clock()} ${old::class.simpleName}->${newState::class.simpleName}")
                while (_transitions.size > 200) _transitions.removeFirst()
            }
        }
        if (persist) saves.trySend(newState)
    }

    companion object {
        const val OFF_ROUTE_MIN_M = 35.0
        const val OFF_ROUTE_CONFIRMATIONS = 3
        const val LOOKAHEAD_SEGMENTS = 400
        const val ARRIVAL_RADIUS_M = 30.0
        const val ARRIVAL_REMAINING_M = 20.0
        const val PERIODIC_SAVE_MS = 15_000L
        const val REROUTE_RETRY_MS = 20_000L
    }
}
