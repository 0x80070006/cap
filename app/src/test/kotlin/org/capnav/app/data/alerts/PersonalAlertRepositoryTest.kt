package org.capnav.app.data.alerts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.capnav.app.data.MemoryTextStore
import org.capnav.app.model.AlertType
import org.capnav.app.model.CreateAlertResult
import org.capnav.app.model.GeoPoint
import org.capnav.app.model.NewPersonalAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PersonalAlertRepositoryTest {
    private val here = GeoPoint(48.8566, 2.3522)
    private val tenMetresAway = GeoPoint(48.85669, 2.3522)
    private val farAway = GeoPoint(48.86, 2.36)
    private var now = 1_700_000_000_000L
    private val store = MemoryTextStore()
    private val repo = PersonalAlertRepository(store, { now }, Dispatchers.Unconfined)

    private suspend fun create(type: AlertType, p: GeoPoint, force: Boolean = false) =
        repo.create(NewPersonalAlert(type, p), force)

    @Test
    fun `creates and persists encrypted-store content`() = runTest {
        val r = create(AlertType.HAZARD, here) as CreateAlertResult.Created
        assertEquals(1, repo.alerts.value.size)
        assertEquals(1, r.alert.passes)
        val reloaded = PersonalAlertRepository(store, { now }, Dispatchers.Unconfined).apply { load() }
        assertEquals(repo.alerts.value, reloaded.alerts.value)
    }

    @Test
    fun `same type within 30 m is a duplicate, other type or force is not`() = runTest {
        create(AlertType.HAZARD, here)
        assertTrue(create(AlertType.HAZARD, tenMetresAway) is CreateAlertResult.Duplicate)
        assertTrue(create(AlertType.POLICE, tenMetresAway) is CreateAlertResult.Created)
        assertTrue(create(AlertType.HAZARD, farAway) is CreateAlertResult.Created)
        assertTrue(create(AlertType.HAZARD, tenMetresAway, force = true) is CreateAlertResult.Created)
        assertEquals(4, repo.alerts.value.size)
    }

    @Test
    fun `merge increments passes without new marker`() = runTest {
        val a = (create(AlertType.TRAFFIC_JAM, here) as CreateAlertResult.Created).alert
        repo.merge(a.id)
        repo.merge(a.id)
        assertEquals(1, repo.alerts.value.size)
        assertEquals(3, repo.alerts.value.single().passes)
    }

    @Test
    fun `alerts never expire`() = runTest {
        create(AlertType.ROAD_CLOSED, here)
        now += 20L * 365 * 24 * 3600 * 1000
        val reloaded = PersonalAlertRepository(store, { now }, Dispatchers.Unconfined).apply { load() }
        assertEquals(1, reloaded.alerts.value.size)
    }

    @Test
    fun `delete, undo and delete all by type`() = runTest {
        val a = (create(AlertType.HAZARD, here) as CreateAlertResult.Created).alert
        create(AlertType.POLICE, farAway)
        create(AlertType.HAZARD, farAway)
        repo.delete(a.id)
        assertFalse(repo.alerts.value.any { it.id == a.id })
        repo.restore(listOf(a))
        assertTrue(repo.alerts.value.any { it.id == a.id })
        repo.deleteAll(AlertType.HAZARD)
        assertEquals(listOf(AlertType.POLICE), repo.alerts.value.map { it.type })
        repo.deleteAll()
        assertTrue(repo.alerts.value.isEmpty())
        assertNull("delete all wipes the store", store.content)
    }

    @Test
    fun `invalid subtype is dropped and notes are sanitised`() = runTest {
        val a = (repo.create(NewPersonalAlert(AlertType.HAZARD, here, subtype = "nope", note = "  hi\u0007 there  ")) as CreateAlertResult.Created).alert
        assertNull(a.subtype)
        assertEquals("hi there", a.note)
        repo.setSubtype(a.id, "pothole")
        assertEquals("pothole", repo.alerts.value.single().subtype)
        assertEquals(PersonalAlertRepository.MAX_NOTE_CHARS, PersonalAlertRepository.sanitizeNote("x".repeat(1000))!!.length)
    }

    @Test
    fun `encrypted export round-trips and rejects a wrong password`() = runTest {
        create(AlertType.HAZARD, here)
        create(AlertType.MAP_NOTE, farAway)
        val blob = repo.exportEncrypted("correct horse".toCharArray()).getOrThrow()
        assertFalse(String(blob, Charsets.ISO_8859_1).contains("HAZARD"))

        val other = PersonalAlertRepository(MemoryTextStore(), { now }, Dispatchers.Unconfined)
        assertTrue(other.importEncrypted(blob, "wrong password".toCharArray()).isFailure)
        assertEquals(2, other.importEncrypted(blob, "correct horse".toCharArray()).getOrThrow())
        assertEquals(0, other.importEncrypted(blob, "correct horse".toCharArray()).getOrThrow())
        assertEquals(setOf(AlertType.HAZARD, AlertType.MAP_NOTE), other.alerts.value.map { it.type }.toSet())
    }

    @Test
    fun `along-route filter keeps only alerts in the corridor`() = runTest {
        val shape = listOf(GeoPoint(48.0, 2.0), GeoPoint(48.0, 2.01))
        create(AlertType.HAZARD, GeoPoint(48.0001, 2.005)) // ~11 m from the line
        create(AlertType.HAZARD, GeoPoint(48.01, 2.005)) // ~1.1 km away
        val hits = repo.alongRoute(repo.alerts.value, shape, 0)
        assertEquals(1, hits.size)
    }

    /**
     * Non-leak guarantee: the personal-alert module must not be able to reach the network.
     * Checked on the source so a future import of a network client fails CI.
     */
    @Test
    fun `personal alert code has no network dependency`() {
        val dir = File("src/main/kotlin/org/capnav/app/data/alerts")
        assertTrue(dir.isDirectory)
        val forbidden = listOf("okhttp", "java.net", "HttpURLConnection", "Socket", "data.network", "OsmNotes", "Http")
        dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
            val text = f.readText()
            forbidden.forEach { word -> assertFalse("${f.name} references $word", text.contains(word)) }
        }
        val fieldTypes = PersonalAlertRepository::class.java.declaredFields.map { it.type.name }
        assertTrue(fieldTypes.none { it.startsWith("okhttp3") || it.startsWith("java.net") })
    }
}
