package org.capnav.app.navigation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.capnav.app.CapApp
import org.capnav.app.MainActivity
import org.capnav.app.R
import org.capnav.app.ui.common.Format

/**
 * Keeps guidance alive with the screen off. Started only while a trip is active, which is also the
 * only time location is used in the background (no ACCESS_BACKGROUND_LOCATION needed).
 */
class NavigationService : LifecycleService() {

    private val container get() = (application as CapApp).container

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
        )
        ServiceCompat.startForeground(
            this, NOTIF_ID, build(getString(R.string.notif_starting), null, paused = false),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
        )
        lifecycleScope.launch {
            container.engine.state.collect { s ->
                when (s) {
                    is TripState.Navigating -> {
                        val p = s.trip.progress
                        val m = s.trip.route.maneuvers.getOrNull(p.maneuverIndex)
                        val units = container.settings.settings.value.units
                        val title = "${Format.distance(p.distanceToManeuverM, units)} · ${m?.instruction.orEmpty()}"
                        val text = getString(
                            R.string.notif_eta, Format.duration(p.etaS),
                            Format.clock(System.currentTimeMillis() + (p.etaS * 1000).toLong()),
                        )
                        nm.notify(NOTIF_ID, build(title, text, paused = false))
                    }
                    is TripState.Paused -> nm.notify(NOTIF_ID, build(getString(R.string.trip_paused), null, paused = true))
                    is TripState.Rerouting -> nm.notify(NOTIF_ID, build(getString(R.string.rerouting), null, paused = false))
                    is TripState.Error -> nm.notify(NOTIF_ID, build(getString(R.string.reroute_failed), null, paused = false))
                    else -> stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val engine = container.engine
        when (intent?.action) {
            ACTION_PAUSE -> lifecycleScope.launch { engine.pause() }
            ACTION_RESUME -> lifecycleScope.launch { engine.resume() }
            ACTION_STOP -> lifecycleScope.launch { engine.stop(StopReason.USER) }
        }
        return START_NOT_STICKY
    }

    private fun build(title: String, text: String?, paused: Boolean): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        fun action(a: String, req: Int) = PendingIntent.getService(
            this, req, Intent(this, NavigationService::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(open)
            .apply {
                if (paused) addAction(0, getString(R.string.resume), action(ACTION_RESUME, 1))
                else addAction(0, getString(R.string.pause), action(ACTION_PAUSE, 2))
                addAction(0, getString(R.string.stop), action(ACTION_STOP, 3))
            }
            .build()
    }

    companion object {
        private const val CHANNEL = "navigation"
        private const val NOTIF_ID = 42
        const val ACTION_PAUSE = "org.capnav.app.PAUSE"
        const val ACTION_RESUME = "org.capnav.app.RESUME"
        const val ACTION_STOP = "org.capnav.app.STOP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, NavigationService::class.java))
        }
    }
}
