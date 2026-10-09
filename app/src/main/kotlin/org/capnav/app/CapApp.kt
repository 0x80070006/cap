package org.capnav.app

import android.app.Application
import org.maplibre.android.MapLibre

class CapApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        MapLibre.getInstance(this)
        container.configureMapLibre()
    }
}
