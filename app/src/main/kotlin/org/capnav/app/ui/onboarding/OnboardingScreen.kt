package org.capnav.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NoAccounts
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.capnav.app.R
import org.capnav.app.ui.CapViewModel
import org.capnav.app.ui.common.PrimaryButton

@Composable
fun OnboardingScreen(vm: CapViewModel, onRequestPermissions: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.ic_logo),
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                )
                Spacer(Modifier.width(16.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(stringResource(R.string.onboarding_lead), style = MaterialTheme.typography.titleMedium)
            Point(Icons.Outlined.NoAccounts, R.string.ob_no_account_title, R.string.ob_no_account)
            Point(Icons.Outlined.Lock, R.string.ob_local_title, R.string.ob_local)
            Point(Icons.Outlined.LocationOn, R.string.ob_location_title, R.string.ob_location)
            Point(Icons.Outlined.Notifications, R.string.ob_notif_title, R.string.ob_notif)
            Point(Icons.Outlined.Dns, R.string.ob_servers_title, R.string.ob_servers)
            Spacer(Modifier.size(8.dp))
            PrimaryButton(stringResource(R.string.ob_continue), {
                vm.updateSettings { it.copy(onboardingDone = true) }
                onRequestPermissions()
            }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Point(icon: ImageVector, title: Int, body: Int) {
    Row {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.fillMaxWidth()) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
