package org.capnav.app.data.geocoding

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.capnav.app.data.network.HttpClients.getString
import org.capnav.app.model.GeoPoint
import org.capnav.app.model.Place
import org.capnav.app.model.PoiCategory
import org.json.JSONObject

interface GeocodingRepository {
    suspend fun search(query: String, bias: GeoPoint?, language: String): Result<List<Place>>
    suspend fun category(category: PoiCategory, near: GeoPoint, limit: Int): Result<List<Place>>
}

/**
 * Photon client (ADR-004). The bias point is rounded to ~1 km before leaving the device:
 * enough for relevance ranking, too coarse to pinpoint the user.
 */
class PhotonGeocoding(
    private val http: OkHttpClient,
    private val baseUrl: () -> String,
) : GeocodingRepository {

    override suspend fun search(query: String, bias: GeoPoint?, language: String): Result<List<Place>> = runCatching {
        parseCoordinates(query)?.let { return@runCatching listOf(it) }
        val q = query.trim().take(MAX_QUERY)
        if (q.length < 2) return@runCatching emptyList()
        val url = baseUrl().trimEnd('/').toHttpUrl().newBuilder()
            .addPathSegment("api")
            .addQueryParameter("q", q)
            .addQueryParameter("limit", "10")
            .addQueryParameter("lang", photonLang(language))
            .apply {
                bias?.let {
                    addQueryParameter("lat", coarse(it.lat))
                    addQueryParameter("lon", coarse(it.lon))
                }
            }
            .build()
        parse(http.getString(url))
    }

    override suspend fun category(category: PoiCategory, near: GeoPoint, limit: Int): Result<List<Place>> = runCatching {
        val url = baseUrl().trimEnd('/').toHttpUrl().newBuilder()
            .addPathSegment("api")
            .addQueryParameter("include", category.osmTag)
            .addQueryParameter("lat", "%.4f".format(java.util.Locale.ROOT, near.lat))
            .addQueryParameter("lon", "%.4f".format(java.util.Locale.ROOT, near.lon))
            .addQueryParameter("limit", limit.coerceIn(1, 20).toString())
            .addQueryParameter("location_bias_scale", "0.1")
            .addQueryParameter("zoom", "14")
            .build()
        parse(http.getString(url)).map { it.copy(category = category.name) }
    }

    companion object {
        private const val MAX_QUERY = 120

        private fun coarse(v: Double) = "%.2f".format(java.util.Locale.ROOT, v)

        private fun photonLang(language: String) = when (language.take(2)) {
            "fr" -> "fr"; "de" -> "de"; "it" -> "it"; else -> "en"
        }

        private val COORDS = Regex("""^\s*(-?\d{1,2}(?:\.\d+)?)\s*[,;\s]\s*(-?\d{1,3}(?:\.\d+)?)\s*$""")

        /** "48.8566, 2.3522" style input is resolved locally, without any request. */
        fun parseCoordinates(input: String): Place? {
            val m = COORDS.matchEntire(input) ?: return null
            val lat = m.groupValues[1].toDouble()
            val lon = m.groupValues[2].toDouble()
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
            return Place("%.5f, %.5f".format(java.util.Locale.ROOT, lat, lon), "", GeoPoint(lat, lon))
        }

        fun parse(text: String): List<Place> {
            val features = JSONObject(text).optJSONArray("features") ?: return emptyList()
            return (0 until features.length()).mapNotNull { i ->
                val f = features.optJSONObject(i) ?: return@mapNotNull null
                val coords = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return@mapNotNull null
                val p = f.optJSONObject("properties") ?: return@mapNotNull null
                val lon = coords.optDouble(0)
                val lat = coords.optDouble(1)
                if (lat.isNaN() || lon.isNaN()) return@mapNotNull null
                val street = listOf(p.optString("housenumber"), p.optString("street")).filter { it.isNotBlank() }.joinToString(" ")
                val name = clean(p.optString("name")).ifEmpty { clean(street) }.ifEmpty { clean(p.optString("city")) }
                if (name.isEmpty()) return@mapNotNull null
                val detail = listOf(street.takeIf { it != name }, p.optString("postcode"), p.optString("city"), p.optString("country"))
                    .filterNot { it.isNullOrBlank() }.joinToString(", ") { clean(it!!) }
                Place(name, detail, GeoPoint(lat, lon), p.optString("osm_value").ifEmpty { null })
            }.distinctBy { it.name to it.detail }
        }

        /** Remote strings are displayed as plain text only; strip control chars and bound the length. */
        private fun clean(s: String) = s.filter { !it.isISOControl() }.trim().take(160)
    }
}
