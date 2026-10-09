package org.capnav.app.navigation

import kotlinx.coroutines.test.runTest
import org.capnav.app.FakeRouting
import org.capnav.app.TestRoutes
import org.capnav.app.data.routing.ValhallaRouting
import org.capnav.app.data.settings.AppSettings
import org.capnav.app.data.settings.SettingsRepository
import org.capnav.app.model.GeoPoint
import org.capnav.app.model.RouteOptions
import org.capnav.app.model.RouteRequest
import org.capnav.app.waypoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetourTest {
    private val origin = GeoPoint(48.0, 2.0)
    // 101 vertices ~ 7.4 km at the model's 120 s total -> fast nominal pace.
    private val route = TestRoutes.straight(origin, vertices = 101)
    private val dest = waypoint(1, route.shape.last())

    private class NoStore : TripStore {
        override suspend fun save(state: TripState) = Unit
        override suspend fun load(): TripState? = null
    }

    @Test
    fun `slow progress produces an observed delay, normal pace does not`() = runTest {
        val e = TripEngine(FakeRouting(), NoStore(), backgroundScope, { 0L }, { "fr" })
        e.preview(listOf(route), origin, listOf(dest), RouteOptions())
        e.start()
        // Crawl: one vertex (~74 m) every 30 s while the model expects ~1.2 s per vertex.
        for (i in 1..6) e.onLocation(TestRoutes.fix(GeoPoint(48.0, 2.0 + i * 0.001 + 0.0002), t = i * 30_000L))
        val delay = (e.state.value as TripState.Navigating).trip.progress.delayS
        assertTrue("delay=$delay", delay > 120.0)

        val fresh = TripEngine(FakeRouting(), NoStore(), backgroundScope, { 0L }, { "fr" })
        fresh.preview(listOf(route), origin, listOf(dest), RouteOptions())
        fresh.start()
        for (i in 1..80) fresh.onLocation(TestRoutes.fix(GeoPoint(48.0, 2.0 + i * 0.001 + 0.0002), t = i * 1_200L))
        assertEquals(0.0, (fresh.state.value as TripState.Navigating).trip.progress.delayS, 1.0)
    }

    @Test
    fun `accepting a detour switches route and keeps remaining stops`() = runTest {
        val e = TripEngine(FakeRouting(), NoStore(), backgroundScope, { 0L }, { "fr" })
        e.preview(listOf(route), origin, listOf(dest), RouteOptions())
        e.start()
        val other = TestRoutes.straight(GeoPoint(48.001, 2.0))
        e.acceptAlternative(other)
        val trip = (e.state.value as TripState.Navigating).trip
        assertEquals(other, trip.route)
        assertEquals(listOf(1L), trip.waypoints.map { it.id })
    }

    @Test
    fun `detour thresholds require both 2 minutes and 8 percent`() {
        assertTrue(TripEngine.worthSwitching(1_800.0, 1_500.0)) // 5 min, 17 %
        assertFalse(TripEngine.worthSwitching(1_800.0, 1_700.0)) // 100 s
        assertFalse(TripEngine.worthSwitching(7_200.0, 6_900.0)) // 5 min but 4 %
    }

    @Test
    fun `exclusion polygons are sent as closed lon-lat rings`() {
        val ring = Geo.corridor(listOf(GeoPoint(48.0, 2.0), GeoPoint(48.0, 2.01)), 25.0)
        assertEquals(ring.first(), ring.last())
        val json = ValhallaRouting.buildRouteJson(
            RouteRequest(origin, listOf(dest), RouteOptions(), "fr", excludePolygons = listOf(ring)),
        )
        val first = json.getJSONArray("exclude_polygons").getJSONArray(0).getJSONArray(0)
        assertEquals(ring.first().lon, first.getDouble(0), 1e-6)
        assertEquals(ring.first().lat, first.getDouble(1), 1e-6)
        // The corridor really straddles the line: one side north, the other south.
        assertTrue(ring.any { it.lat > 48.0 } && ring.any { it.lat < 48.0 })
    }

    @Test
    fun `zoom and tilt settings are clamped, default to a flat close view and round-trip`() {
        val d = AppSettings()
        assertEquals(0f, d.navTilt)
        assertTrue(d.navZoom >= 18f)
        val s = SettingsRepository.sanitize(AppSettings(navZoom = 30f, navTilt = 90f))
        assertEquals(AppSettings.MAX_NAV_ZOOM, s.navZoom)
        assertEquals(AppSettings.MAX_NAV_TILT, s.navTilt)
        assertEquals(s, SettingsRepository.decode(SettingsRepository.encode(s)))
    }
}
