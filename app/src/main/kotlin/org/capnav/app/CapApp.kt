package org.capnav.app

import android.app.Application
import android.content.Context
import org.capnav.app.ui.settings.AppLanguage
import org.maplibre.android.MapLibre

class CapApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguage.wrap(base))
    }

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        MapLibre.getInstance(this)
        container.configureMapLibre()
    }
}
