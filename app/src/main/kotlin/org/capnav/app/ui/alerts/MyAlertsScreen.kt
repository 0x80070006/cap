package org.capnav.app.ui.alerts

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.capnav.app.R
import org.capnav.app.data.settings.AppSettings
import org.capnav.app.model.AlertId
import org.capnav.app.model.AlertType
import org.capnav.app.navigation.Geo
import org.capnav.app.ui.CapViewModel
import org.capnav.app.ui.common.AlertBadge
import org.capnav.app.ui.common.Format
import org.capnav.app.ui.common.ScreenHeader
import org.capnav.app.ui.common.SecondaryButton
import org.capnav.app.ui.common.SectionTitle
import org.capnav.app.ui.common.subtypeLabel
import org.capnav.app.ui.common.visual
import org.capnav.app.ui.theme.LocalSemantic
import kotlin.math.roundToInt

private enum class Sort { DATE, DISTANCE, TYPE }

private sealed interface PasswordAction {
    data class Export(val uri: android.net.Uri) : PasswordAction
    data class Import(val uri: android.net.Uri) : PasswordAction
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MyAlertsScreen(vm: CapViewModel) {
    val alerts by vm.alerts.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val fix by vm.fix.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var filter by remember { mutableStateOf<AlertType?>(null) }
    var sort by remember { mutableStateOf(Sort.DATE) }
    var search by remember { mutableStateOf("") }
    var selection by remember { mutableStateOf(emptySet<AlertId>()) }
    var menu by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf<Pair<Boolean, AlertType?>?>(null) }
    var pickTypeToDelete by remember { mutableStateOf(false) }
    var passwordFor by remember { mutableStateOf<PasswordAction?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) passwordFor = PasswordAction.Export(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) passwordFor = PasswordAction.Import(uri)
    }

    val visible = remember(alerts, filter, sort, search, fix) {
        val q = search.trim().lowercase()
        alerts.filter { filter == null || it.type == filter }
            .filter { a ->
                q.isEmpty() || context.getString(a.type.visual().label).lowercase().contains(q) ||
                    a.note.orEmpty().lowercase().contains(q) ||
                    (a.subtype?.let { context.getString(subtypeLabel(it)).lowercase().contains(q) } ?: false)
            }
            .let { list ->
                when (sort) {
                    Sort.DATE -> list.sortedByDescending { it.createdAt }
                    Sort.TYPE -> list.sortedBy { it.type.ordinal }
                    Sort.DISTANCE -> fix?.let { f -> list.sortedBy { Geo.distanceM(f.point, it.point) } } ?: list
                }
            }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
            ScreenHeader(stringResource(R.string.my_alerts), onBack = { vm.screen = org.capnav.app.ui.Screen.MAP }) {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Outlined.MoreVert, stringResource(R.string.more))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.export_alerts)) }, leadingIcon = { Icon(Icons.Outlined.Upload, null) },
                        onClick = { menu = false; exportLauncher.launch("cap-alerts.capx") })
                    DropdownMenuItem(text = { Text(stringResource(R.string.import_alerts)) }, leadingIcon = { Icon(Icons.Outlined.Download, null) },
                        onClick = { menu = false; importLauncher.launch(arrayOf("*/*")) })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete_all_of_type)) },
                        onClick = { menu = false; pickTypeToDelete = true })
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete_all)) },
                        onClick = { menu = false; confirmDeleteAll = true to null })
                }
            }

            LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                item { AlertDisplaySettings(vm, settings) }
                item {
                    OutlinedTextField(
                        value = search, onValueChange = { search = it.take(80) },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        placeholder = { Text(stringResource(R.string.search_alerts)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = sort == Sort.DATE, onClick = { sort = Sort.DATE }, label = { Text(stringResource(R.string.sort_date)) })
                        FilterChip(selected = sort == Sort.DISTANCE, onClick = { sort = Sort.DISTANCE }, label = { Text(stringResource(R.string.sort_distance)) })
                        FilterChip(selected = sort == Sort.TYPE, onClick = { sort = Sort.TYPE }, label = { Text(stringResource(R.string.sort_type)) })
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text(stringResource(R.string.all_types)) })
                        alerts.map { it.type }.distinct().sortedBy { it.ordinal }.forEach { t ->
                            FilterChip(selected = filter == t, onClick = { filter = if (filter == t) null else t }, label = { Text(stringResource(t.visual().label)) })
                        }
                    }
                }
                if (visible.isEmpty()) item {
                    Text(
                        stringResource(if (alerts.isEmpty()) R.string.no_alerts_yet else R.string.no_matching_alerts),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 32.dp),
                    )
                }
                items(visible, key = { it.id.value }) { a ->
                    val selected = a.id in selection
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 72.dp)
                            .combinedClickable(
                                onClick = {
                                    if (selection.isNotEmpty()) selection = if (selected) selection - a.id else selection + a.id
                                    else vm.showOnMap(a.point).also { vm.detailAlert = a.id }
                                },
                                onLongClick = { selection = selection + a.id },
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (selection.isNotEmpty()) Checkbox(checked = selected, onCheckedChange = {
                            selection = if (selected) selection - a.id else selection + a.id
                        })
                        AlertBadge(a.type, alpha = settings.alertOpacity.coerceAtLeast(0.6f))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            val title = a.subtype?.let { stringResource(subtypeLabel(it)) } ?: stringResource(a.type.visual().label)
                            Text(title, style = MaterialTheme.typography.titleMedium)
                            val meta = buildList {
                                add(Format.date(a.createdAt))
                                fix?.let { add(Format.distance(Geo.distanceM(it.point, a.point), settings.units)) }
                                if (a.passes > 1) add(context.getString(R.string.passes_n, a.passes))
                            }.joinToString(" · ")
                            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            a.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                    HorizontalDivider()
                }
            }

            if (selection.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { selection = emptySet() }) { Text(stringResource(R.string.cancel)) }
                        TextButton(onClick = { selection = visible.map { it.id }.toSet() }) { Text(stringResource(R.string.select_all)) }
                        Spacer(Modifier.weight(1f))
                        SecondaryButton(
                            stringResource(R.string.delete_n, selection.size),
                            { vm.deleteAlerts(selection); selection = emptySet() },
                            icon = Icons.Outlined.Delete, color = LocalSemantic.current.danger,
                        )
                    }
                }
            }
        }
    }

    if (pickTypeToDelete) AlertDialog(
        onDismissRequest = { pickTypeToDelete = false },
        title = { Text(stringResource(R.string.delete_all_of_type)) },
        text = {
            Column {
                alerts.map { it.type }.distinct().forEach { t ->
                    TextButton(onClick = { pickTypeToDelete = false; confirmDeleteAll = true to t }, modifier = Modifier.fillMaxWidth()) {
                        Text("${stringResource(t.visual().label)} (${alerts.count { it.type == t }})", Modifier.fillMaxWidth())
                    }
                }
                if (alerts.isEmpty()) Text(stringResource(R.string.no_alerts_yet))
            }
        },
        confirmButton = { TextButton(onClick = { pickTypeToDelete = false }) { Text(stringResource(R.string.cancel)) } },
    )

    confirmDeleteAll?.let { (_, type) ->
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = null },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = {
                val n = alerts.count { type == null || it.type == type }
                Text(stringResource(R.string.confirm_delete_text, n))
            },
            confirmButton = {
                TextButton(onClick = { vm.deleteAllAlerts(type); confirmDeleteAll = null }) {
                    Text(stringResource(R.string.delete), color = LocalSemantic.current.danger)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    passwordFor?.let { action -> PasswordDialog(isExport = action is PasswordAction.Export, onDismiss = { passwordFor = null }) { pw ->
        when (action) {
            is PasswordAction.Export -> vm.exportAlerts(action.uri, pw)
            is PasswordAction.Import -> vm.importAlerts(action.uri, pw)
        }
        passwordFor = null
    } }
}

@Composable
private fun AlertDisplaySettings(vm: CapViewModel, settings: AppSettings) {
    Column {
        SectionTitle(stringResource(R.string.display))
        Text(
            stringResource(R.string.alert_opacity, (settings.alertOpacity * 100).roundToInt()),
            style = MaterialTheme.typography.bodyLarge,
        )
        Slider(
            value = settings.alertOpacity,
            onValueChange = { v -> vm.updateSettings { it.copy(alertOpacity = v) } },
            valueRange = AppSettings.MIN_ALERT_OPACITY..AppSettings.MAX_ALERT_OPACITY,
        )
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.approach_reminders), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = settings.approachReminders, onCheckedChange = { c -> vm.updateSettings { it.copy(approachReminders = c) } })
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.show_alerts_on_map), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = settings.alertsVisible, onCheckedChange = { c -> vm.updateSettings { it.copy(alertsVisible = c) } })
        }
    }
}

@Composable
private fun PasswordDialog(isExport: Boolean, onDismiss: () -> Unit, onConfirm: (CharArray) -> Unit) {
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    val valid = pw.length >= 8 && (!isExport || pw == pw2)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isExport) R.string.export_alerts else R.string.import_alerts)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(if (isExport) R.string.export_explain else R.string.import_explain), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = pw, onValueChange = { pw = it }, label = { Text(stringResource(R.string.password)) },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                if (isExport) OutlinedTextField(
                    value = pw2, onValueChange = { pw2 = it }, label = { Text(stringResource(R.string.password_confirm)) },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onConfirm(pw.toCharArray()) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
