package org.capnav.app.data.places

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.capnav.app.data.TextStore
import org.capnav.app.model.GeoPoint
import org.capnav.app.model.Place
import org.json.JSONArray
import org.json.JSONObject

enum class FavoriteKind { HOME, WORK, CUSTOM }

/** Icon chosen by the user for a favourite; stored by name so new icons never break old data. */
enum class FavoriteIcon { STAR, HOME, WORK, HEART, FAMILY, SCHOOL, SPORT, SHOP, RESTAURANT, CAFE, HOSPITAL, PARKING, FUEL, TRAIN, PARK, BEACH }

data class Favorite(
    val kind: FavoriteKind,
    val place: Place,
    val label: String? = null,
    val icon: FavoriteIcon = when (kind) {
        FavoriteKind.HOME -> FavoriteIcon.HOME
        FavoriteKind.WORK -> FavoriteIcon.WORK
        FavoriteKind.CUSTOM -> FavoriteIcon.STAR
    },
) {
    val displayName: String get() = label ?: place.name
}

data class PlacesData(val favorites: List<Favorite> = emptyList(), val recents: List<Place> = emptyList()) {
    val home get() = favorites.firstOrNull { it.kind == FavoriteKind.HOME }
    val work get() = favorites.firstOrNull { it.kind == FavoriteKind.WORK }
}

/** Favourites and recent destinations, encrypted on the device and never synchronised. */
class PlacesRepository(private val store: TextStore) {
    private val mutex = Mutex()
    private val _data = MutableStateFlow(PlacesData())
    val data: StateFlow<PlacesData> = _data.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock { _data.value = store.read()?.let(::decode) ?: PlacesData() }
    }

    suspend fun addRecent(place: Place) = edit { d ->
        d.copy(recents = (listOf(place) + d.recents.filterNot { it.point == place.point }).take(MAX_RECENTS))
    }

    suspend fun setFavorite(kind: FavoriteKind, place: Place) = saveFavorite(null, Favorite(kind, place))

    /** Adds [fav] or replaces [original]. Home and Work are unique; customs are unique per point. */
    suspend fun saveFavorite(original: Favorite?, fav: Favorite) = edit { d ->
        val clean = fav.copy(label = fav.label?.let(::sanitizeLabel))
        val others = d.favorites.filterNot {
            it == original || (clean.kind != FavoriteKind.CUSTOM && it.kind == clean.kind) ||
                (it.kind == FavoriteKind.CUSTOM && clean.kind == FavoriteKind.CUSTOM && it.place.point == clean.place.point)
        }
        d.copy(favorites = others + clean)
    }

    suspend fun removeFavorite(fav: Favorite) = edit { d -> d.copy(favorites = d.favorites - fav) }

    suspend fun clearRecents() = edit { it.copy(recents = emptyList()) }

    suspend fun wipe() = withContext(Dispatchers.IO) {
        mutex.withLock {
            store.wipe()
            _data.value = PlacesData()
        }
    }

    private suspend fun edit(f: (PlacesData) -> PlacesData) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = f(_data.value)
            store.write(encode(next))
            _data.value = next
        }
    }

    companion object {
        const val MAX_RECENTS = 30
        const val MAX_LABEL = 40

        fun sanitizeLabel(raw: String): String? = raw.filter { !it.isISOControl() }.trim().take(MAX_LABEL).ifEmpty { null }

        fun placeToJson(p: Place) = JSONObject().put("n", p.name).put("d", p.detail)
            .put("lat", p.point.lat).put("lon", p.point.lon).apply { p.category?.let { put("c", it) } }

        fun placeFromJson(o: JSONObject) = Place(
            o.getString("n"), o.optString("d"), GeoPoint(o.getDouble("lat"), o.getDouble("lon")),
            o.optString("c").ifEmpty { null },
        )

        private fun encode(d: PlacesData) = JSONObject()
            .put("fav", JSONArray().apply {
                d.favorites.forEach {
                    put(JSONObject().put("k", it.kind.name).put("p", placeToJson(it.place)).put("i", it.icon.name)
                        .apply { it.label?.let { l -> put("l", l) } })
                }
            })
            .put("rec", JSONArray().apply { d.recents.forEach { put(placeToJson(it)) } })
            .toString()

        private fun decode(text: String): PlacesData = runCatching {
            val o = JSONObject(text)
            val fav = o.optJSONArray("fav") ?: JSONArray()
            val rec = o.optJSONArray("rec") ?: JSONArray()
            PlacesData(
                favorites = (0 until fav.length()).mapNotNull { i ->
                    val f = fav.getJSONObject(i)
                    runCatching {
                        val kind = FavoriteKind.valueOf(f.getString("k"))
                        val base = Favorite(kind, placeFromJson(f.getJSONObject("p")))
                        base.copy(
                            label = f.optString("l").ifEmpty { null }?.let(::sanitizeLabel),
                            icon = runCatching { FavoriteIcon.valueOf(f.getString("i")) }.getOrDefault(base.icon),
                        )
                    }.getOrNull()
                },
                recents = (0 until rec.length()).mapNotNull { i -> runCatching { placeFromJson(rec.getJSONObject(i)) }.getOrNull() },
            )
        }.getOrDefault(PlacesData())
    }
}
