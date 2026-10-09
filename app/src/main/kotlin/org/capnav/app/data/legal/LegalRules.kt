package org.capnav.app.data.legal

import org.capnav.app.model.AlertType
import org.json.JSONObject

data class CountryRules(val speedCameraAlerts: Boolean, val policeAlerts: Boolean)

/**
 * Versioned per-country rules (data/legal/rules.json, bundled as an asset). Unknown countries
 * get the most restrictive defaults.
 */
class LegalRules(json: String) {
    val version: Int
    private val rules: Map<String, CountryRules>

    init {
        val root = JSONObject(json)
        version = root.getInt("version")
        val countries = root.getJSONObject("countries")
        rules = countries.keys().asSequence().associateWith { code ->
            val o = countries.getJSONObject(code)
            CountryRules(o.optBoolean("speedCameraAlerts", false), o.optBoolean("policeAlerts", false))
        }
    }

    fun forCountry(code: String) = rules[code.uppercase()] ?: RESTRICTIVE

    fun isAllowed(type: AlertType, country: String): Boolean {
        val r = forCountry(country)
        return when (type) {
            AlertType.SPEED_CAMERA -> r.speedCameraAlerts
            AlertType.POLICE -> r.policeAlerts
            else -> true
        }
    }

    val countries: List<String> get() = rules.keys.sorted()

    companion object {
        val RESTRICTIVE = CountryRules(speedCameraAlerts = false, policeAlerts = false)
    }
}
