package org.capnav.app.data.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.capnav.app.data.TextStore
import org.json.JSONArray
import org.json.JSONObject

enum class Purpose { GEOCODING, ROUTING, POI_SEARCH, MAP_TILES, OSM_NOTE }

/**
 * What left the device, as shown in the privacy dashboard. Entries are aggregated per
 * (purpose, host, minute) so the log describes volumes without becoming a location trace:
 * it never stores URLs, query strings or coordinates.
 */
data class NetworkLogEntry(
    val minuteEpoch: Long,
    val purpose: Purpose,
    val host: String,
    val requests: Int,
    val bytesSent: Long,
    val bytesReceived: Long,
)

class NetworkLog(private val store: TextStore, private val clock: () -> Long = System::currentTimeMillis) {
    private val lock = Any()
    private val _entries = MutableStateFlow(store.read()?.let(::decode).orEmpty())
    val entries: StateFlow<List<NetworkLogEntry>> = _entries.asStateFlow()
    private var dirtySince = 0L

    fun record(purpose: Purpose, host: String, sent: Long, received: Long) = synchronized(lock) {
        val minute = clock() / 60_000
        val list = _entries.value.toMutableList()
        val last = list.lastOrNull()
        if (last != null && last.minuteEpoch == minute && last.purpose == purpose && last.host == host) {
            list[list.lastIndex] = last.copy(
                requests = last.requests + 1,
                bytesSent = last.bytesSent + sent,
                bytesReceived = last.bytesReceived + received,
            )
        } else {
            list += NetworkLogEntry(minute, purpose, host, 1, sent, received)
        }
        while (list.size > MAX_ENTRIES) list.removeAt(0)
        _entries.value = list
        // Tile bursts produce many records; persist at most every 10 s.
        if (clock() - dirtySince > 10_000) {
            dirtySince = clock()
            store.write(encode(list))
        }
    }

    fun flush() = synchronized(lock) { store.write(encode(_entries.value)) }

    fun clear() = synchronized(lock) {
        store.wipe()
        _entries.value = emptyList()
    }

    private companion object {
        const val MAX_ENTRIES = 1_000

        fun encode(list: List<NetworkLogEntry>) = JSONArray().apply {
            list.forEach {
                put(JSONObject().put("m", it.minuteEpoch).put("p", it.purpose.name).put("h", it.host)
                    .put("n", it.requests).put("s", it.bytesSent).put("r", it.bytesReceived))
            }
        }.toString()

        fun decode(text: String): List<NetworkLogEntry> = runCatching {
            val arr = JSONArray(text)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val p = runCatching { Purpose.valueOf(o.getString("p")) }.getOrNull() ?: return@mapNotNull null
                NetworkLogEntry(o.getLong("m"), p, o.getString("h"), o.getInt("n"), o.getLong("s"), o.getLong("r"))
            }
        }.getOrDefault(emptyList())
    }
}
