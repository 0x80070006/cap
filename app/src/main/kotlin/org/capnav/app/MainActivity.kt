package org.capnav.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import org.capnav.app.ui.settings.AppLanguage
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.capnav.app.ui.CapRoot
import org.capnav.app.ui.CapViewModel

class MainActivity : ComponentActivity() {

    private val vm: CapViewModel by lazy {
        val container = (application as CapApp).container
        ViewModelProvider(this, viewModelFactory { initializer { CapViewModel(container, application) } })[CapViewModel::class.java]
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { reportLocationState() }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { CapRoot(vm, onRequestPermissions = ::requestPermissionsIfNeeded) }
    }

    override fun onStart() {
        super.onStart()
        reportLocationState()
    }

    private fun granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    /** Precise = FINE granted; Android 12+ lets users grant only COARSE ("approximate"). */
    private fun hasLocation() = granted(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun reportLocationState() {
        val coarse = granted(Manifest.permission.ACCESS_COARSE_LOCATION)
        val gps = getSystemService(LocationManager::class.java)?.isProviderEnabled(LocationManager.GPS_PROVIDER) ?: false
        vm.onLocationPermission(granted = hasLocation() || coarse, precise = hasLocation(), gpsOn = gps)
    }

    /** Asked at the moment of use (after onboarding), never at install time. */
    private fun requestPermissionsIfNeeded() {
        val wanted = buildList {
            if (!hasLocation()) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (wanted.isEmpty()) reportLocationState() else permissionLauncher.launch(wanted.toTypedArray())
    }
}
