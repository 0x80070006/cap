package org.capnav.app

import android.Manifest
import android.content.pm.PackageManager
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
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.onLocationPermission(hasLocation()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { CapRoot(vm, onRequestPermissions = ::requestPermissionsIfNeeded) }
    }

    override fun onStart() {
        super.onStart()
        vm.onLocationPermission(hasLocation())
    }

    private fun hasLocation() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

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
        if (wanted.isEmpty()) vm.onLocationPermission(true) else permissionLauncher.launch(wanted.toTypedArray())
    }
}
