package org.capnav.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.capnav.app.R
import org.capnav.app.data.network.Purpose
import org.capnav.app.ui.CapViewModel
import org.capnav.app.ui.Screen
import org.capnav.app.ui.common.Format
import org.capnav.app.ui.common.ScreenHeader
import org.capnav.app.ui.common.SecondaryButton
import org.capnav.app.ui.common.SectionTitle
import org.capnav.app.ui.theme.LocalSemantic

@Composable
fun PrivacyScreen(vm: CapViewModel) {
    val log by vm.networkLog.collectAsStateWithLifecycle()
    val alerts by vm.alerts.collectAsStateWithLifecycle()
    val places by vm.places.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    var confirmWipe by remember { mutableStateOf(false) }
    var showDiag by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
            ScreenHeader(stringResource(R.string.privacy_dashboard), onBack = { vm.screen = Screen.SETTINGS })
            LazyColumn(Modifier.padding(horizontal = 16.dp)) {
                item {
                    SectionTitle(stringResource(R.string.stays_on_device))
                    Text(stringResource(R.string.stays_on_device_body), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.size(8.dp))
                    Line(stringResource(R.string.alerts), alerts.size.toString())
                    Line(stringResource(R.string.favorites), places.favorites.size.toString())
                    Line(stringResource(R.string.recents), places.recents.size.toString())

                    SectionTitle(stringResource(R.string.leaves_device))
                    Flow(stringResource(R.string.flow_search), s.geocoderUrl)
                    Flow(stringResource(R.string.flow_routing), s.routingUrl)
                    Flow(stringResource(R.string.flow_tiles), s.styleDayUrl)
                    Flow(stringResource(R.string.flow_osm), "https://api.openstreetmap.org")
                    Text(stringResource(R.string.never_sent), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    SectionTitle(stringResource(R.string.network_journal))
                    Purpose.entries.forEach { p ->
                        val e = log.filter { it.purpose == p }
                        if (e.isNotEmpty()) Line(
                            stringResource(purposeLabel(p)),
                            stringResource(R.string.requests_bytes, e.sumOf { it.requests }, kb(e.sumOf { it.bytesSent }), kb(e.sumOf { it.bytesReceived })),
                        )
                    }
                    if (log.isEmpty()) Text(stringResource(R.string.journal_empty), style = MaterialTheme.typography.bodyMedium)
                }
                items(log.takeLast(60).reversed()) { e ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(
                            "${Format.clock(e.minuteEpoch * 60_000)} · ${stringResource(purposeLabel(e.purpose))}",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            "${e.host} — ${stringResource(R.string.requests_bytes, e.requests, kb(e.bytesSent), kb(e.bytesReceived))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider()
                }
                item {
                    Spacer(Modifier.size(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton(stringResource(R.string.clear_journal), vm::clearNetworkLog, Modifier.fillMaxWidth())
                        SecondaryButton(stringResource(R.string.clear_recents), { vm.clearRecents() }, Modifier.fillMaxWidth())
                        SecondaryButton(
                            stringResource(R.string.erase_everything), { confirmWipe = true }, Modifier.fillMaxWidth(),
                            Icons.Outlined.DeleteForever, color = LocalSemantic.current.danger,
                        )
                    }
                    SectionTitle(stringResource(R.string.diagnostics))
                    TextButton(onClick = { showDiag = !showDiag }) { Text(stringResource(R.string.trip_transitions)) }
                    if (showDiag) vm.transitions().takeLast(30).forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.size(24.dp))
                }
            }
        }
    }

    if (confirmWipe) AlertDialog(
        onDismissRequest = { confirmWipe = false },
        title = { Text(stringResource(R.string.erase_everything)) },
        text = { Text(stringResource(R.string.erase_everything_text)) },
        confirmButton = {
            TextButton(onClick = { confirmWipe = false; vm.wipeEverything() }) {
                Text(stringResource(R.string.erase), color = LocalSemantic.current.danger)
            }
        },
        dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun kb(bytes: Long) = (bytes + 1023) / 1024

fun purposeLabel(p: Purpose) = when (p) {
    Purpose.GEOCODING -> R.string.purpose_geocoding
    Purpose.ROUTING -> R.string.purpose_routing
    Purpose.POI_SEARCH -> R.string.purpose_poi
    Purpose.MAP_TILES -> R.string.purpose_tiles
    Purpose.OSM_NOTE -> R.string.purpose_osm
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Flow(what: String, url: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(what, style = MaterialTheme.typography.bodyMedium)
        Text("→ ${url.toHttpUrlOrNull()?.host ?: url}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
