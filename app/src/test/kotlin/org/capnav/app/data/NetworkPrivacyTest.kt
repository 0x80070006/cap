package org.capnav.app.data

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.capnav.app.data.geocoding.PhotonGeocoding
import org.capnav.app.data.network.HttpClients
import org.capnav.app.data.network.NetworkLog
import org.capnav.app.data.network.Purpose
import org.capnav.app.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkPrivacyTest {

    @Test
    fun `geocoding bias is rounded to about 1 km and no identifier is sent`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"type":"FeatureCollection","features":[]}"""))
        server.start()
        val log = NetworkLog(MemoryTextStore())
        val client = OkHttpClient.Builder()
            .addInterceptor(HttpClients.logging(log) { Purpose.GEOCODING })
            .build()
        PhotonGeocoding(client) { server.url("/").toString() }.search("gare", GeoPoint(48.856613, 2.352222), "fr-FR").getOrThrow()
        val req = server.takeRequest()
        assertEquals("48.86", req.requestUrl!!.queryParameter("lat"))
        assertEquals("2.35", req.requestUrl!!.queryParameter("lon"))
        assertNull(req.getHeader("Cookie"))
        assertNull(req.getHeader("Authorization"))
        server.shutdown()

        val entry = log.entries.value.single()
        assertEquals(Purpose.GEOCODING, entry.purpose)
        assertEquals(1, entry.requests)
    }

    @Test
    fun `privacy log stores no url, query or coordinates`() {
        val store = MemoryTextStore()
        val log = NetworkLog(store, clock = { 0L })
        repeat(5) { log.record(Purpose.MAP_TILES, "tiles.example.org", 100, 2_000) }
        log.flush()
        assertEquals(1, log.entries.value.size)
        assertEquals(5, log.entries.value.single().requests)
        val persisted = store.content!!
        assertFalse(persisted.contains("http"))
        assertFalse(persisted.contains("?"))
        assertTrue(persisted.contains("tiles.example.org"))
    }

    @Test
    fun `hardened client refuses cleartext`() = runTest {
        val server = MockWebServer()
        server.start()
        val client = HttpClients.base().build()
        val result = runCatching {
            client.newCall(okhttp3.Request.Builder().url(server.url("/")).build()).execute()
        }
        assertTrue(result.isFailure)
        assertEquals(0, server.requestCount)
        server.shutdown()
    }
}
