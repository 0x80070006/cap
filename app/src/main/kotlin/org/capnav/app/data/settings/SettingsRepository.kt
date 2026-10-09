package org.capnav.app.data.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.capnav.app.data.TextStore
import org.capnav.app.model.AlertType
import org.capnav.app.model.RouteOptions
import org.capnav.app.model.VehicleProfile
import org.json.JSONArray
import org.json.JSONObject

enum class ThemeMode { AUTO, LIGHT, DARK }

enum class Units { METRIC, IMPERIAL }

data class AppSettings(
    val onboardingDone: Boolean = false,
    val theme: ThemeMode = ThemeMode.AUTO,
    val highContrast: Boolean = false,
    val accessibleTrafficPalette: Boolean = false,
    val units: Units = Units.METRIC,
    val voiceEnabled: Boolean = true,
    val alertOpacity: Float = 0.45f,
    val alertsVisible: Boolean = true,
    val hiddenAlertTypes: Set<AlertType> = emptySet(),
    val approachReminders: Boolean = true,
    val reminderDisabledTypes: Set<AlertType> = emptySet(),
    val reportButtonLeft: Boolean = false,
    val country: String = "FR",
    val routeOptions: RouteOptions = RouteOptions(),
    val routingUrl: String = DEFAULT_ROUTING_URL,
    val geocoderUrl: String = DEFAULT_GEOCODER_URL,
    val styleDayUrl: String = DEFAULT_STYLE_DAY,
    val styleNightUrl: String = DEFAULT_STYLE_NIGHT,
) {
    companion object {
        const val DEFAULT_ROUTING_URL = "https://valhalla1.openstreetmap.de"
        const val DEFAULT_GEOCODER_URL = "https://photon.komoot.io"
        const val DEFAULT_STYLE_DAY = "https://tiles.openfreemap.org/styles/positron"
        const val DEFAULT_STYLE_NIGHT = "https://tiles.openfreemap.org/styles/dark"
        const val MIN_ALERT_OPACITY = 0.20f
        const val MAX_ALERT_OPACITY = 0.70f
    }
}

class SettingsRepository(private val store: TextStore) {
    private val mutex = Mutex()
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock { _settings.value = store.read()?.let(::decode) ?: AppSettings() }
    }

    suspend fun update(f: (AppSettings) -> AppSettings) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = sanitize(f(_settings.value))
            store.write(encode(next))
            _settings.value = next
        }
    }

    suspend fun reset() = withContext(Dispatchers.IO) {
        mutex.withLock {
            store.wipe()
            _settings.value = AppSettings()
        }
    }

    companion object {
        private val HTTPS = Regex("""^https://[A-Za-z0-9.\-]+(:\d{1,5})?(/[A-Za-z0-9._~\-/]*)?$""")

        fun isValidServerUrl(url: String) = HTTPS.matches(url.trim())

        fun sanitize(s: AppSettings) = s.copy(
            alertOpacity = s.alertOpacity.coerceIn(AppSettings.MIN_ALERT_OPACITY, AppSettings.MAX_ALERT_OPACITY),
            routingUrl = s.routingUrl.trim().takeIf(::isValidServerUrl) ?: AppSettings.DEFAULT_ROUTING_URL,
            geocoderUrl = s.geocoderUrl.trim().takeIf(::isValidServerUrl) ?: AppSettings.DEFAULT_GEOCODER_URL,
            styleDayUrl = s.styleDayUrl.trim().takeIf(::isValidServerUrl) ?: AppSettings.DEFAULT_STYLE_DAY,
            styleNightUrl = s.styleNightUrl.trim().takeIf(::isValidServerUrl) ?: AppSettings.DEFAULT_STYLE_NIGHT,
            country = s.country.uppercase().filter { it in 'A'..'Z' }.take(2).ifEmpty { "FR" },
        )

        private fun types(set: Set<AlertType>) = JSONArray().apply { set.forEach { put(it.name) } }

        private fun typesOf(arr: JSONArray?): Set<AlertType> =
            if (arr == null) emptySet()
            else (0 until arr.length()).mapNotNull { runCatching { AlertType.valueOf(arr.getString(it)) }.getOrNull() }.toSet()

        fun encode(s: AppSettings): String = JSONObject()
            .put("onboarding", s.onboardingDone)
            .put("theme", s.theme.name)
            .put("hc", s.highContrast)
            .put("a11yTraffic", s.accessibleTrafficPalette)
            .put("units", s.units.name)
            .put("voice", s.voiceEnabled)
            .put("alertOpacity", s.alertOpacity.toDouble())
            .put("alertsVisible", s.alertsVisible)
            .put("hiddenTypes", types(s.hiddenAlertTypes))
            .put("reminders", s.approachReminders)
            .put("reminderOff", types(s.reminderDisabledTypes))
            .put("reportLeft", s.reportButtonLeft)
            .put("country", s.country)
            .put("avoidTolls", s.routeOptions.avoidTolls)
            .put("avoidHighways", s.routeOptions.avoidHighways)
            .put("avoidFerries", s.routeOptions.avoidFerries)
            .put("avoidUnpaved", s.routeOptions.avoidUnpaved)
            .put("vehicle", s.routeOptions.vehicle.name)
            .put("routingUrl", s.routingUrl)
            .put("geocoderUrl", s.geocoderUrl)
            .put("styleDay", s.styleDayUrl)
            .put("styleNight", s.styleNightUrl)
            .toString()

        fun decode(text: String): AppSettings = runCatching {
            val o = JSONObject(text)
            val d = AppSettings()
            sanitize(
                AppSettings(
                    onboardingDone = o.optBoolean("onboarding", d.onboardingDone),
                    theme = runCatching { ThemeMode.valueOf(o.getString("theme")) }.getOrDefault(d.theme),
                    highContrast = o.optBoolean("hc", d.highContrast),
                    accessibleTrafficPalette = o.optBoolean("a11yTraffic", d.accessibleTrafficPalette),
                    units = runCatching { Units.valueOf(o.getString("units")) }.getOrDefault(d.units),
                    voiceEnabled = o.optBoolean("voice", d.voiceEnabled),
                    alertOpacity = o.optDouble("alertOpacity", d.alertOpacity.toDouble()).toFloat(),
                    alertsVisible = o.optBoolean("alertsVisible", d.alertsVisible),
                    hiddenAlertTypes = typesOf(o.optJSONArray("hiddenTypes")),
                    approachReminders = o.optBoolean("reminders", d.approachReminders),
                    reminderDisabledTypes = typesOf(o.optJSONArray("reminderOff")),
                    reportButtonLeft = o.optBoolean("reportLeft", d.reportButtonLeft),
                    country = o.optString("country", d.country),
                    routeOptions = RouteOptions(
                        avoidTolls = o.optBoolean("avoidTolls"),
                        avoidHighways = o.optBoolean("avoidHighways"),
                        avoidFerries = o.optBoolean("avoidFerries"),
                        avoidUnpaved = o.optBoolean("avoidUnpaved"),
                        vehicle = runCatching { VehicleProfile.valueOf(o.getString("vehicle")) }.getOrDefault(VehicleProfile.CAR),
                    ),
                    routingUrl = o.optString("routingUrl", d.routingUrl),
                    geocoderUrl = o.optString("geocoderUrl", d.geocoderUrl),
                    styleDayUrl = o.optString("styleDay", d.styleDayUrl),
                    styleNightUrl = o.optString("styleNight", d.styleNightUrl),
                ),
            )
        }.getOrDefault(AppSettings())
    }
}
