package org.capnav.app.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.capnav.app.model.GeoPoint
import org.capnav.app.navigation.Fix

enum class LocationProfile(val intervalMs: Long, val minDistanceM: Float) {
    NAVIGATION(1_000, 0f),
    BROWSING(4_000, 10f),
    BATTERY_SAVER(10_000, 25f),
}

interface LocationProvider {
    fun locationUpdates(profile: LocationProfile): Flow<Fix>
}

/**
 * Platform LocationManager only (no Google Play Services, ADR-005). GPS is primary; the network
 * provider is used only when the platform offers one (absent on many de-Googled ROMs).
 */
class NativeLocationProvider(context: Context) : LocationProvider {
    private val lm = context.getSystemService(LocationManager::class.java)

    @SuppressLint("MissingPermission")
    override fun locationUpdates(profile: LocationProfile): Flow<Fix> = callbackFlow {
        val listener = LocationListener { loc -> trySend(loc.toFix()) }
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.let { trySend(it.toFix()) }
        try {
            providers.forEach {
                lm.requestLocationUpdates(it, profile.intervalMs, profile.minDistanceM, listener, Looper.getMainLooper())
            }
        } catch (e: SecurityException) {
            close(e)
        }
        awaitClose { lm.removeUpdates(listener) }
    }

    private fun Location.toFix() = Fix(
        point = GeoPoint(latitude, longitude),
        speedMps = if (hasSpeed()) speed else 0f,
        bearingDeg = if (hasBearing()) bearing else null,
        accuracyM = if (hasAccuracy()) accuracy else 50f,
        timeMs = time,
    )
}
