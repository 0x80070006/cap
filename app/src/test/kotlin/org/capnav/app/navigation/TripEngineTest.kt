package org.capnav.app.navigation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.capnav.app.FakeRouting
import org.capnav.app.TestRoutes
import org.capnav.app.model.GeoPoint
import org.capnav.app.model.RouteOptions
import org.capnav.app.waypoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TripEngineTest {
    private val origin = GeoPoint(48.0, 2.0)
    private val route = TestRoutes.straight(origin)
    private val dest = waypoint(1, route.shape.last())

    private class MemoryStore : TripStore {
        var saved: TripState? = null
        var saves = 0
        override suspend fun save(state: TripState) {
            saved = state; saves++
        }
        override suspend fun load() = saved
    }

    private fun TestScope.engine(routing: FakeRouting = FakeRouting(), store: TripStore = MemoryStore(), now: () -> Long = { 1_000L }) =
        TripEngine(routing, store, backgroundScope, now, { "fr-FR" })

    private suspend fun TripEngine.navigating() {
        preview(listOf(route), origin, listOf(dest), RouteOptions())
        start()
    }

    @Test
    fun `idle - preview - start - navigating`() = runTest {
        val e = engine()
        assertEquals(TripState.Idle, e.state.value)
        e.preview(listOf(route, route), origin, listOf(dest), RouteOptions())
        assertTrue(e.state.value is TripState.Previewing)
        e.select(1)
        assertEquals(1, (e.state.value as TripState.Previewing).selected)
        e.start()
        assertTrue(e.state.value is TripState.Navigating)
    }

    @Test
    fun `cancel preview returns to idle`() = runTest {
        val e = engine()
        e.preview(listOf(route), origin, listOf(dest), RouteOptions())
        e.cancelPreview()
        assertEquals(TripState.Idle, e.state.value)
    }

    @Test
    fun `progress follows the route and counts down`() = runTest {
        val e = engine()
        e.navigating()
        e.onLocation(TestRoutes.fix(GeoPoint(48.0, 2.0052)))
        val p = (e.state.value as TripState.Navigating).trip.progress
        assertEquals(5, p.segmentIndex)
        assertEquals(1, p.maneuverIndex)
        assertTrue(p.remainingM < route.lengthM)
        assertTrue(p.distanceToManeuverM in 300.0..400.0)
    }

    @Test
    fun `pause keeps the trip and resume reroutes from current position`() = runTest {
        val routing = FakeRouting()
        val e = engine(routing)
        e.navigating()
        e.onLocation(TestRoutes.fix(route.shape[3]))
        e.pause()
        val paused = e.state.value as TripState.Paused
        assertEquals(route, paused.trip.route)
        // Fixes while paused don't move progress.
        e.onLocation(TestRoutes.fix(route.shape[8]))
        assertTrue(e.state.value is TripState.Paused)
        e.resume()
        assertTrue(e.state.value is TripState.Navigating)
        assertEquals(route.shape[8], routing.requests.last().origin)
    }

    @Test
    fun `single off-route fix does not reroute but three do`() = runTest {
        val routing = FakeRouting()
        val e = engine(routing)
        e.navigating()
        val off = GeoPoint(48.01, 2.005) // ~1.1 km north
        e.onLocation(TestRoutes.fix(off))
        assertTrue(e.state.value is TripState.Navigating)
        e.onLocation(TestRoutes.fix(route.shape[2]))
        e.onLocation(TestRoutes.fix(off))
        e.onLocation(TestRoutes.fix(off))
        assertTrue("hysteresis reset by on-route fix", e.state.value is TripState.Navigating)
        e.onLocation(TestRoutes.fix(off))
        assertEquals(1, routing.requests.size)
        assertEquals(off, routing.requests.single().origin)
        assertTrue(e.state.value is TripState.Navigating)
    }

    @Test
    fun `failed reroute goes to error and recovers when back on route`() = runTest {
        val routing = FakeRouting().apply { fail = true }
        var now = 1_000L
        val e = engine(routing, now = { now })
        e.navigating()
        val off = GeoPoint(48.01, 2.005)
        repeat(3) { e.onLocation(TestRoutes.fix(off)) }
        assertTrue(e.state.value is TripState.Error)
        now += 1_000
        e.onLocation(TestRoutes.fix(route.shape[4]))
        assertTrue(e.state.value is TripState.Navigating)
    }

    @Test
    fun `arrival finishes the trip`() = runTest {
        val e = engine()
        e.navigating()
        e.onLocation(TestRoutes.fix(route.shape[10]))
        e.onLocation(TestRoutes.fix(route.shape.last()))
        val s = e.state.value as TripState.Finished
        assertTrue(s.reachedDestination)
        e.dismissSummary()
        assertEquals(TripState.Idle, e.state.value)
    }

    @Test
    fun `user stop produces a summary that is not an arrival`() = runTest {
        val e = engine()
        e.navigating()
        e.stop(StopReason.USER)
        assertFalse((e.state.value as TripState.Finished).reachedDestination)
    }

    @Test
    fun `adding a stop while driving inserts it next and reroutes`() = runTest {
        val routing = FakeRouting()
        val e = engine(routing)
        e.navigating()
        e.onLocation(TestRoutes.fix(route.shape[2]))
        val stop = waypoint(2, GeoPoint(48.001, 2.01))
        e.addWaypoint(stop, 0)
        val trip = (e.state.value as TripState.Navigating).trip
        assertEquals(listOf(2L, 1L), trip.waypoints.map { it.id })
        assertEquals(listOf(2L, 1L), routing.requests.last().waypoints.map { it.id })
    }

    @Test
    fun `destination cannot be removed and reorder keeps all stops`() = runTest {
        val e = engine()
        e.navigating()
        e.removeWaypoint(1)
        assertEquals(listOf(1L), (e.state.value as TripState.Navigating).trip.waypoints.map { it.id })
        e.addWaypoint(waypoint(2, GeoPoint(48.001, 2.01)))
        e.addWaypoint(waypoint(3, GeoPoint(48.001, 2.012)))
        e.reorderWaypoints(listOf(3, 2, 1))
        assertEquals(listOf(3L, 2L, 1L), (e.state.value as TripState.Navigating).trip.waypoints.map { it.id })
        e.skipWaypoint()
        assertEquals(listOf(2L, 1L), (e.state.value as TripState.Navigating).trip.waypoints.map { it.id })
    }

    @Test
    fun `paused state is persisted and an interrupted trip restores as paused`() = runTest {
        val store = MemoryStore()
        val e = engine(store = store)
        e.navigating()
        runCurrent()
        assertTrue(store.saved is TripState.Navigating)
        // Simulated process death: a fresh engine restores the last saved state.
        val restored = engine(store = store)
        restored.restore()
        assertTrue(restored.state.value is TripState.Paused)
    }

    @Test
    fun `transitions are logged without coordinates`() = runTest {
        val e = engine()
        e.navigating()
        e.pause()
        val log = e.transitions
        assertEquals(3, log.size)
        assertTrue(log.all { line -> !line.contains("48.") && !line.contains("2.0") })
        assertTrue(log.last().endsWith("Navigating->Paused"))
    }

    @Test
    fun `announcements fire once per stage`() = runTest {
        val e = engine()
        val events = mutableListOf<TripEvent>()
        val job = backgroundScope.launchCollect(e, events)
        e.navigating()
        e.onLocation(TestRoutes.fix(route.shape[7], speed = 5f))
        e.onLocation(TestRoutes.fix(route.shape[7], speed = 5f))
        e.onLocation(TestRoutes.fix(GeoPoint(48.0, 2.0093), speed = 5f))
        e.onLocation(TestRoutes.fix(GeoPoint(48.0, 2.0093), speed = 5f))
        runCurrent()
        val texts = events.filterIsInstance<TripEvent.Announce>().map { it.text }
        assertEquals(1, texts.count { it == "Turn right now" })
        assertEquals(1, texts.count { it.endsWith("Turn right onto Rue B") })
        job.cancel()
    }

    private fun kotlinx.coroutines.CoroutineScope.launchCollect(e: TripEngine, into: MutableList<TripEvent>) =
        launch(kotlinx.coroutines.test.UnconfinedTestDispatcher()) { e.events.collect { into += it } }
}
