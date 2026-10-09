package org.capnav.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.capnav.app.BuildConfig
import org.capnav.app.R
import org.capnav.app.ui.CapViewModel
import org.capnav.app.ui.Screen
import org.capnav.app.ui.common.ScreenHeader
import org.capnav.app.ui.common.SectionTitle

private val LICENSES = listOf(
    "OpenStreetMap data" to "© OpenStreetMap contributors — ODbL 1.0",
    "OpenFreeMap / OpenMapTiles schema" to "© OpenFreeMap, © OpenMapTiles — BSD-3 / CC-BY 4.0",
    "MapLibre Native Android" to "BSD-2-Clause",
    "Valhalla (routing server)" to "MIT",
    "Photon (geocoding server)" to "Apache-2.0",
    "Jetpack Compose, AndroidX" to "Apache-2.0",
    "Kotlin, kotlinx.coroutines" to "Apache-2.0",
    "OkHttp" to "Apache-2.0",
    "Material Symbols / Icons" to "Apache-2.0",
)

@Composable
fun AboutScreen(vm: CapViewModel) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
            ScreenHeader(stringResource(R.string.about_licenses), onBack = { vm.screen = Screen.SETTINGS })
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                Text("${stringResource(R.string.app_name)} ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.about_body), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.size(8.dp))
                Text("https://github.com/0x80070006/cap", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                SectionTitle(stringResource(R.string.licenses))
                LICENSES.forEach { (name, lic) ->
                    Text(name, style = MaterialTheme.typography.titleSmall)
                    Text(lic, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.size(8.dp))
                }
                SectionTitle(stringResource(R.string.disclaimer))
                Text(stringResource(R.string.disclaimer_body), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.size(24.dp))
            }
        }
    }
}
