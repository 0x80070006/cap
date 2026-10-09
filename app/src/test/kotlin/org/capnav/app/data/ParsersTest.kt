package org.capnav.app.data

import org.capnav.app.data.geocoding.PhotonGeocoding
import org.capnav.app.data.legal.LegalRules
import org.capnav.app.data.routing.ValhallaRouting
import org.capnav.app.data.settings.AppSettings
import org.capnav.app.data.settings.SettingsRepository
import org.capnav.app.model.AlertType
import org.capnav.app.model.GeoPoint
import org.capnav.app.model.ManeuverKind
import org.capnav.app.model.RouteLabel
import org.capnav.app.model.RouteOptions
import org.capnav.app.model.RouteRequest
import org.capnav.app.navigation.Geo
import org.capnav.app.navigation.TripCodec
import org.capnav.app.navigation.ActiveTrip
import org.capnav.app.navigation.Progress
import org.capnav.app.waypoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ParsersTest {
    private fun resource(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    @Test
    fun `valhalla response with alternates`() {
        val routes = ValhallaRouting.parseRouteResponse(resource("valhalla_route_alternates.json"))
        assertEquals(3, routes.size)
        assertEquals(RouteLabel.FASTEST, routes[0].label)
        assertTrue(routes.drop(1).all { it.label == RouteLabel.ALTERNATIVE })
        routes.forEach { r ->
            assertTrue(r.shape.size > 10)
            assertEquals(ManeuverKind.DEPART, r.maneuvers.first().kind)
            assertEquals(ManeuverKind.ARRIVE, r.maneuvers.last().kind)
            assertTrue(r.maneuvers.all { it.shapeIndex in r.shape.indices })
            // Shape should roughly match the reported length.
            val geomLen = Geo.cumulative(r.shape).last()
            assertTrue(kotlin.math.abs(geomLen - r.lengthM) / r.lengthM < 0.05)
        }
    }

    @Test
    fun `valhalla multi-leg response keeps leg boundaries`() {
        val r = ValhallaRouting.parseRouteResponse(resource("valhalla_route_multileg.json")).single()
        assertEquals(2, r.legEnds.size)
        assertEquals(r.shape.lastIndex, r.legEnds.last())
        assertTrue(r.maneuvers.any { it.kind == ManeuverKind.WAYPOINT })
        assertEquals(ManeuverKind.ARRIVE, r.maneuvers.last().kind)
    }

    @Test
    fun `route request encodes options and asks alternates only for single destination`() {
        val one = ValhallaRouting.buildRouteJson(
            RouteRequest(GeoPoint(48.0, 2.0), listOf(waypoint(1, GeoPoint(48.1, 2.1))), RouteOptions(avoidTolls = true), "fr-FR"),
        )
        assertEquals(2, one.getInt("alternates"))
        assertEquals(0.0, one.getJSONObject("costing_options").getJSONObject("auto").getDouble("use_tolls"), 0.0)
        val two = ValhallaRouting.buildRouteJson(
            RouteRequest(GeoPoint(48.0, 2.0), listOf(waypoint(1, GeoPoint(48.1, 2.1)), waypoint(2, GeoPoint(48.2, 2.2))), RouteOptions(), "fr-FR"),
        )
        assertFalse(two.has("alternates"))
        assertEquals(3, two.getJSONArray("locations").length())
    }

    @Test
    fun `photon results are parsed and cleaned`() {
        val places = PhotonGeocoding.parse(resource("photon_search.json"))
        assertTrue(places.isNotEmpty())
        assertTrue(places.first().name.contains("Lyon"))
        assertTrue(places.all { p -> p.name.none { it.isISOControl() } })
    }

    @Test
    fun `coordinates are resolved locally`() {
        assertEquals(GeoPoint(48.8566, 2.3522), PhotonGeocoding.parseCoordinates("48.8566, 2.3522")!!.point)
        assertNotNull(PhotonGeocoding.parseCoordinates("-33.9 18.4"))
        assertNull(PhotonGeocoding.parseCoordinates("95, 10"))
        assertNull(PhotonGeocoding.parseCoordinates("rue de Rivoli"))
    }

    @Test
    fun `settings refuse non https servers and clamp opacity`() {
        val s = SettingsRepository.sanitize(AppSettings(routingUrl = "http://evil.example", alertOpacity = 0.95f))
        assertEquals(AppSettings.DEFAULT_ROUTING_URL, s.routingUrl)
        assertEquals(AppSettings.MAX_ALERT_OPACITY, s.alertOpacity)
        assertTrue(SettingsRepository.isValidServerUrl("https://valhalla.example.org:8443/api"))
        assertFalse(SettingsRepository.isValidServerUrl("https://x.org/?q=<script>"))
        val round = SettingsRepository.decode(SettingsRepository.encode(s))
        assertEquals(s, round)
    }

    @Test
    fun `legal rules are conservative`() {
        val rules = LegalRules(File("../data/legal/rules.json").readText())
        assertFalse(rules.isAllowed(AlertType.SPEED_CAMERA, "FR"))
        assertTrue(rules.isAllowed(AlertType.POLICE, "FR"))
        assertFalse(rules.isAllowed(AlertType.POLICE, "CH"))
        assertFalse(rules.isAllowed(AlertType.SPEED_CAMERA, "ZZ"))
        assertTrue(rules.isAllowed(AlertType.HAZARD, "ZZ"))
    }

    @Test
    fun `trip codec round-trips an active trip`() {
        val r = ValhallaRouting.parseRouteResponse(resource("valhalla_route_multileg.json")).single()
        val trip = ActiveTrip(
            r, listOf(waypoint(1, GeoPoint(48.853, 2.3499)), waypoint(2, GeoPoint(48.8443, 2.3743))),
            RouteOptions(avoidHighways = true), Progress.initial(r), 123L, r.durationS, 42.0,
        )
        val back = TripCodec.decodeTrip(TripCodec.encodeTrip(trip))
        assertEquals(trip.route, back.route)
        assertEquals(trip.waypoints, back.waypoints)
        assertEquals(trip.options, back.options)
    }

    @Test
    fun `polyline6 decoding`() {
        // Encoded by Valhalla for [(38.5, -120.2), (40.7, -120.95), (43.252, -126.453)] at precision 6.
        val pts = Geo.decodePolyline6("_izlhA~rlgdF_{geC~ywl@_kwzCn`{nI")
        assertEquals(3, pts.size)
        assertEquals(38.5, pts[0].lat, 1e-6)
        assertEquals(-120.2, pts[0].lon, 1e-6)
        assertEquals(43.252, pts[2].lat, 1e-6)
        assertEquals(-126.453, pts[2].lon, 1e-6)
    }
}
