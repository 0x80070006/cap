package org.capnav.app.ui.common

import org.capnav.app.data.settings.Units
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object Format {
    fun distance(m: Double, units: Units, locale: Locale = Locale.getDefault()): String = when (units) {
        Units.METRIC -> when {
            m < 1_000 -> "${roundTo(m, if (m < 100) 10 else 50)} m"
            m < 10_000 -> String.format(locale, "%.1f km", m / 1000)
            else -> "${(m / 1000).roundToInt()} km"
        }
        Units.IMPERIAL -> {
            val miles = m / 1609.344
            when {
                miles < 0.1 -> "${roundTo(m * 3.28084, 50)} ft"
                miles < 10 -> String.format(locale, "%.1f mi", miles)
                else -> "${miles.roundToInt()} mi"
            }
        }
    }

    /** Spoken form, e.g. "300 mètres", built from resources by the caller; here only the number. */
    fun spokenDistance(m: Double, units: Units): Pair<Int, Boolean> = when (units) {
        Units.METRIC -> if (m < 1_000) roundTo(m, 50) to false else (m / 1000).roundToInt() to true
        Units.IMPERIAL -> if (m < 300) roundTo(m * 3.28084, 100) to false else (m / 1609.344).roundToInt().coerceAtLeast(1) to true
    }

    fun duration(s: Double): String {
        val totalMin = (s / 60).roundToInt().coerceAtLeast(0)
        return if (totalMin < 60) "$totalMin min" else "${totalMin / 60} h ${"%02d".format(totalMin % 60)}"
    }

    fun clock(epochMs: Long, locale: Locale = Locale.getDefault()): String =
        DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(Date(epochMs))

    fun date(epochMs: Long, locale: Locale = Locale.getDefault()): String =
        DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(epochMs))

    fun speed(mps: Float, units: Units): Int = when (units) {
        Units.METRIC -> (mps * 3.6f).roundToInt()
        Units.IMPERIAL -> (mps * 2.236936f).roundToInt()
    }

    fun speedUnit(units: Units) = if (units == Units.METRIC) "km/h" else "mph"

    private fun roundTo(v: Double, step: Int): Int = ((v / step).roundToInt() * step).coerceAtLeast(step.coerceAtMost(10))
}
