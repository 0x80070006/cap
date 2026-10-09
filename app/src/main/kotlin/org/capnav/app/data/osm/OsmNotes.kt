package org.capnav.app.data.osm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.capnav.app.model.GeoPoint
import java.util.Locale

/**
 * Anonymous OpenStreetMap note. Only ever called after the user saw [preview] and confirmed;
 * personal alerts are otherwise never transmitted.
 */
class OsmNotes(private val http: OkHttpClient) {

    data class Draft(val point: GeoPoint, val text: String)

    fun preview(point: GeoPoint, text: String): Draft = Draft(
        GeoPoint(round5(point.lat), round5(point.lon)),
        text.filter { !it.isISOControl() || it == '\n' }.trim().take(MAX_TEXT),
    )

    suspend fun send(draft: Draft): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = FormBody.Builder()
                .add("lat", String.format(Locale.ROOT, "%.5f", draft.point.lat))
                .add("lon", String.format(Locale.ROOT, "%.5f", draft.point.lon))
                .add("text", draft.text)
                .build()
            val req = Request.Builder().url(ENDPOINT).post(body).build()
            http.newCall(req).execute().use { res -> check(res.isSuccessful) { "HTTP ${res.code}" } }
        }
    }

    companion object {
        const val ENDPOINT = "https://api.openstreetmap.org/api/0.6/notes"
        const val HOST = "api.openstreetmap.org"
        private const val MAX_TEXT = 1_000
        private fun round5(v: Double) = Math.round(v * 1e5) / 1e5
    }
}
