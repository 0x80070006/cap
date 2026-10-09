package org.capnav.app.data.alerts

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.capnav.app.data.TextStore
import org.capnav.app.model.AlertId
import org.capnav.app.model.AlertType
import org.capnav.app.model.BoundingBox
import org.capnav.app.model.CreateAlertResult
import org.capnav.app.model.GeoPoint
import org.capnav.app.model.NewPersonalAlert
import org.capnav.app.model.PersonalAlert
import org.capnav.app.navigation.Geo
import org.capnav.app.security.PasswordBox
import org.json.JSONArray
import org.json.JSONObject

/**
 * Personal alerts: 100 % local. This class has no network dependency by construction (it only
 * receives a [TextStore]); alerts never expire and are only removed by an explicit user action.
 */
class PersonalAlertRepository(
    private val store: TextStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()
    private val _alerts = MutableStateFlow<List<PersonalAlert>>(emptyList())
    val alerts: StateFlow<List<PersonalAlert>> = _alerts.asStateFlow()
    private var loaded = false

    suspend fun load() = withContext(io) {
        mutex.withLock { ensureLoaded() }
    }

    private fun ensureLoaded() {
        if (loaded) return
        _alerts.value = store.read()?.let(::decode).orEmpty()
        loaded = true
    }

    suspend fun create(new: NewPersonalAlert, force: Boolean = false): CreateAlertResult = withContext(io) {
        mutex.withLock {
            ensureLoaded()
            if (!force) {
                val dup = _alerts.value.firstOrNull {
                    it.type == new.type && Geo.distanceM(it.point, new.point) <= DEDUP_RADIUS_M
                }
                if (dup != null) return@withLock CreateAlertResult.Duplicate(dup)
            }
            val alert = PersonalAlert(
                id = AlertId(nextId()),
                type = new.type,
                subtype = new.subtype?.takeIf { it in new.type.subtypes },
                point = new.point,
                createdAt = clock(),
                note = new.note?.let(::sanitizeNote),
                passes = 1,
            )
            commit(_alerts.value + alert)
            CreateAlertResult.Created(alert)
        }
    }

    /** "Merge" on duplicate: counts one more pass at the existing place instead of a new marker. */
    suspend fun merge(id: AlertId) = update(id) { it.copy(passes = it.passes + 1) }

    suspend fun setSubtype(id: AlertId, subtype: String?) =
        update(id) { a -> a.copy(subtype = subtype?.takeIf { it in a.type.subtypes }) }

    suspend fun setNote(id: AlertId, note: String?) = update(id) { it.copy(note = note?.let(::sanitizeNote)) }

    private suspend fun update(id: AlertId, f: (PersonalAlert) -> PersonalAlert) = withContext(io) {
        mutex.withLock {
            ensureLoaded()
            commit(_alerts.value.map { if (it.id == id) f(it) else it })
        }
    }

    suspend fun delete(id: AlertId) = deleteMany(setOf(id))

    suspend fun deleteMany(ids: Set<AlertId>) = withContext(io) {
        mutex.withLock {
            ensureLoaded()
            commit(_alerts.value.filterNot { it.id in ids })
        }
    }

    suspend fun deleteAll(type: AlertType? = null) = withContext(io) {
        mutex.withLock {
            ensureLoaded()
            val remaining = if (type == null) emptyList() else _alerts.value.filterNot { it.type == type }
            if (remaining.isEmpty()) {
                store.wipe()
                _alerts.value = emptyList()
            } else {
                commit(remaining)
            }
        }
    }

    /** Undo support: puts back alerts removed a few seconds ago, with their original ids. */
    suspend fun restore(removed: Collection<PersonalAlert>) = withContext(io) {
        mutex.withLock {
            ensureLoaded()
            val ids = _alerts.value.map { it.id }.toSet()
            commit((_alerts.value + removed.filterNot { it.id in ids }).sortedBy { it.createdAt })
        }
    }

    fun observeInBounds(bbox: BoundingBox, types: Set<AlertType>): Flow<List<PersonalAlert>> =
        alerts.map { list -> list.filter { it.type in types && bbox.contains(it.point) } }

    /** Alerts lying within [corridorM] of the route ahead, ordered by distance along the route. */
    fun alongRoute(alerts: List<PersonalAlert>, shape: List<GeoPoint>, fromSegment: Int, corridorM: Double = 40.0):
        List<Pair<PersonalAlert, Int>> =
        alerts.mapNotNull { a ->
            val proj = Geo.project(a.point, shape, fromSegment) ?: return@mapNotNull null
            if (proj.distanceM <= corridorM) a to proj.segmentIndex else null
        }.sortedBy { it.second }

    suspend fun exportEncrypted(password: CharArray): Result<ByteArray> = withContext(io) {
        runCatching {
            val json = mutex.withLock {
                ensureLoaded()
                toGeoJson(_alerts.value)
            }
            PasswordBox.seal(json.toByteArray(Charsets.UTF_8), password)
        }
    }

    suspend fun importEncrypted(data: ByteArray, password: CharArray): Result<Int> = withContext(io) {
        runCatching {
            require(data.size <= MAX_IMPORT_BYTES) { "file too large" }
            val imported = fromGeoJson(String(PasswordBox.open(data, password), Charsets.UTF_8))
            mutex.withLock {
                ensureLoaded()
                val existing = _alerts.value
                val fresh = imported.filterNot { i ->
                    existing.any { it.type == i.type && it.createdAt == i.createdAt && it.point == i.point }
                }
                var id = nextId()
                val withIds = fresh.map { it.copy(id = AlertId(id++)) }
                commit(existing + withIds)
                withIds.size
            }
        }
    }

    private fun nextId(): Long = (_alerts.value.maxOfOrNull { it.id.value } ?: 0L) + 1

    private fun commit(list: List<PersonalAlert>) {
        store.write(encode(list))
        _alerts.value = list
    }

    companion object {
        const val DEDUP_RADIUS_M = 30.0
        const val MAX_NOTE_CHARS = 280
        const val MAX_IMPORT_BYTES = 8 shl 20

        fun sanitizeNote(raw: String): String? =
            raw.filter { !it.isISOControl() || it == '\n' }.trim().take(MAX_NOTE_CHARS).ifEmpty { null }

        internal fun encode(list: List<PersonalAlert>): String = JSONArray().apply {
            list.forEach { a ->
                put(JSONObject().apply {
                    put("id", a.id.value)
                    put("type", a.type.name)
                    a.subtype?.let { put("sub", it) }
                    put("lat", a.point.lat)
                    put("lon", a.point.lon)
                    put("at", a.createdAt)
                    a.note?.let { put("note", it) }
                    put("passes", a.passes)
                })
            }
        }.toString()

        internal fun decode(text: String): List<PersonalAlert> {
            val arr = runCatching { JSONArray(text) }.getOrNull() ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val type = runCatching { AlertType.valueOf(o.getString("type")) }.getOrNull() ?: return@mapNotNull null
                val lat = o.optDouble("lat")
                val lon = o.optDouble("lon")
                if (lat.isNaN() || lon.isNaN() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return@mapNotNull null
                PersonalAlert(
                    id = AlertId(o.getLong("id")),
                    type = type,
                    subtype = o.optString("sub").takeIf { it.isNotEmpty() && it in type.subtypes },
                    point = GeoPoint(lat, lon),
                    createdAt = o.optLong("at"),
                    note = o.optString("note").takeIf { it.isNotEmpty() }?.let(::sanitizeNote),
                    passes = o.optInt("passes", 1).coerceAtLeast(1),
                )
            }
        }

        internal fun toGeoJson(list: List<PersonalAlert>): String = JSONObject().apply {
            put("type", "FeatureCollection")
            put("features", JSONArray().apply {
                list.forEach { a ->
                    put(JSONObject().apply {
                        put("type", "Feature")
                        put("geometry", JSONObject().apply {
                            put("type", "Point")
                            put("coordinates", JSONArray().put(a.point.lon).put(a.point.lat))
                        })
                        put("properties", JSONObject().apply {
                            put("type", a.type.name)
                            a.subtype?.let { put("subtype", it) }
                            put("createdAt", a.createdAt)
                            a.note?.let { put("note", it) }
                            put("passes", a.passes)
                        })
                    })
                }
            })
        }.toString()

        internal fun fromGeoJson(text: String): List<PersonalAlert> {
            val features = JSONObject(text).getJSONArray("features")
            require(features.length() <= 100_000) { "too many features" }
            return (0 until features.length()).mapNotNull { i ->
                val f = features.optJSONObject(i) ?: return@mapNotNull null
                val props = f.optJSONObject("properties") ?: return@mapNotNull null
                val coords = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return@mapNotNull null
                val type = runCatching { AlertType.valueOf(props.getString("type")) }.getOrNull() ?: return@mapNotNull null
                val lon = coords.optDouble(0)
                val lat = coords.optDouble(1)
                if (lat.isNaN() || lon.isNaN() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return@mapNotNull null
                PersonalAlert(
                    id = AlertId(0),
                    type = type,
                    subtype = props.optString("subtype").takeIf { it.isNotEmpty() && it in type.subtypes },
                    point = GeoPoint(lat, lon),
                    createdAt = props.optLong("createdAt"),
                    note = props.optString("note").takeIf { it.isNotEmpty() }?.let(::sanitizeNote),
                    passes = props.optInt("passes", 1).coerceAtLeast(1),
                )
            }
        }
    }
}
