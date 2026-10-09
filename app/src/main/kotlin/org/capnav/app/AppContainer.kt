package org.capnav.app

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.capnav.app.data.EncryptedTextStore
import org.capnav.app.data.TextStore
import org.capnav.app.data.alerts.PersonalAlertRepository
import org.capnav.app.data.geocoding.PhotonGeocoding
import org.capnav.app.data.legal.LegalRules
import org.capnav.app.data.location.NativeLocationProvider
import org.capnav.app.data.network.HttpClients
import org.capnav.app.data.network.NetworkLog
import org.capnav.app.data.network.Purpose
import org.capnav.app.data.osm.OsmNotes
import org.capnav.app.data.places.PlacesRepository
import org.capnav.app.data.routing.ValhallaRouting
import org.capnav.app.data.settings.SettingsRepository
import org.capnav.app.navigation.AnnouncementFormatter
import org.capnav.app.navigation.EncryptedTripStore
import org.capnav.app.navigation.TripEngine
import org.capnav.app.navigation.Voice
import org.capnav.app.security.EncryptedFile
import org.capnav.app.security.KeystoreCipher
import org.capnav.app.ui.common.Format
import org.maplibre.android.module.http.HttpRequestUtil
import java.io.File
import java.util.Locale

/** Manual dependency graph (ADR-008): small, explicit, no reflection. */
class AppContainer(private val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val cipher = KeystoreCipher()
    private val storeFiles = mutableListOf<TextStore>()

    private fun store(name: String): TextStore =
        EncryptedTextStore(EncryptedFile(File(app.noBackupFilesDir, "cap/$name.bin"), cipher)).also { storeFiles += it }

    val settings = SettingsRepository(store("settings"))
    val places = PlacesRepository(store("places"))
    val alerts = PersonalAlertRepository(store("alerts"))
    val networkLog by lazy { NetworkLog(store("netlog")) }
    private val tripStore = EncryptedTripStore(store("trip"))

    private fun purposeOf(url: HttpUrl): Purpose {
        val s = settings.settings.value
        return when (url.host) {
            s.routingUrl.toHttpUrlOrNull()?.host ->
                if (url.encodedPath.contains("sources_to_targets")) Purpose.POI_SEARCH else Purpose.ROUTING
            s.geocoderUrl.toHttpUrlOrNull()?.host ->
                if (url.queryParameter("include") != null) Purpose.POI_SEARCH else Purpose.GEOCODING
            OsmNotes.HOST -> Purpose.OSM_NOTE
            else -> Purpose.MAP_TILES
        }
    }

    val http: OkHttpClient by lazy {
        HttpClients.base().addInterceptor(HttpClients.logging(networkLog, ::purposeOf)).build()
    }

    val geocoding by lazy { PhotonGeocoding(http) { settings.settings.value.geocoderUrl } }
    val routing by lazy { ValhallaRouting(http, { settings.settings.value.routingUrl }, geocoding) }
    val osmNotes by lazy { OsmNotes(http) }
    val location by lazy { NativeLocationProvider(app) }
    val voice by lazy { Voice(app) }
    val legal by lazy { LegalRules(app.assets.open("legal/rules.json").bufferedReader().use { it.readText() }) }

    val engine: TripEngine by lazy {
        TripEngine(
            routing = routing,
            store = tripStore,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            clock = System::currentTimeMillis,
            language = { Locale.getDefault().toLanguageTag() },
            formatter = AnnouncementFormatter { d, text ->
                val (n, km) = Format.spokenDistance(d, settings.settings.value.units)
                val res = app.resources
                val dist = if (settings.settings.value.units == org.capnav.app.data.settings.Units.METRIC) {
                    if (km) res.getQuantityString(R.plurals.voice_km, n, n) else res.getQuantityString(R.plurals.voice_m, n, n)
                } else {
                    if (km) res.getQuantityString(R.plurals.voice_mi, n, n) else res.getQuantityString(R.plurals.voice_ft, n, n)
                }
                app.getString(R.string.voice_in_distance, dist, text.replaceFirstChar { it.lowercase(Locale.getDefault()) })
            },
        )
    }

    /** Map tiles go through the same hardened, privacy-logged client as everything else. */
    fun configureMapLibre() {
        HttpRequestUtil.setOkHttpClient(http)
    }

    /** "Erase everything": wipes every store, then destroys the key (crypto-shredding any remnant). */
    suspend fun wipeEverything() {
        alerts.deleteAll()
        places.wipe()
        engine.stop(org.capnav.app.navigation.StopReason.USER)
        engine.dismissSummary()
        networkLog.clear()
        storeFiles.forEach { it.wipe() }
        settings.reset()
        cipher.destroyKey()
    }
}
