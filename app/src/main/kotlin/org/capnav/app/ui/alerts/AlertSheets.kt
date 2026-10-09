package org.capnav.app.ui.alerts

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.capnav.app.R
import org.capnav.app.data.alerts.PersonalAlertRepository
import org.capnav.app.model.AlertType
import org.capnav.app.model.PersonalAlert
import org.capnav.app.ui.CapViewModel
import org.capnav.app.ui.common.AlertBadge
import org.capnav.app.ui.common.ExtraReportTypes
import org.capnav.app.ui.common.Format
import org.capnav.app.ui.common.KeyValue
import org.capnav.app.ui.common.PrimaryButton
import org.capnav.app.ui.common.ReportGrid
import org.capnav.app.ui.common.SecondaryButton
import org.capnav.app.ui.common.subtypeLabel
import org.capnav.app.ui.common.visual
import org.capnav.app.ui.theme.LocalSemantic
import java.util.Locale

/** All modal surfaces related to personal alerts, driven by view-model state. */
@Composable
fun AlertSheets(vm: CapViewModel) {
    if (vm.reportTarget != null) ReportSheet(vm)
    vm.duplicate?.let { (_, existing) -> DuplicateDialog(vm, existing) }
    vm.roadsideHelp?.let { RoadsideHelpDialog(vm, it) }
    vm.noteEditor?.let { NoteEditorDialog(vm, it) }
    vm.detailAlert?.let { id ->
        val alerts by vm.alerts.collectAsStateWithLifecycle()
        val alert = alerts.firstOrNull { it.id == id }
        if (alert != null) AlertDetailSheet(vm, alert) else LaunchedEffect(id) { vm.detailAlert = null }
    }
    vm.osmDraft?.let { OsmNoteDialog(vm, it) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportSheet(vm: CapViewModel) {
    ModalBottomSheet(onDismissRequest = vm::closeReport, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.what_do_you_see), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = vm::closeReport, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Outlined.Close, stringResource(R.string.close))
                }
            }
            Text(
                stringResource(R.string.alerts_stay_local),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(12.dp))
            val created = vm.lastCreated
            if (created != null && created.type in ReportGrid + ExtraReportTypes) {
                CreatedConfirmation(vm, created)
            } else {
                val types = if (vm.showExtraTypes) ExtraReportTypes else ReportGrid
                types.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        row.forEach { t -> ReportCell(t, vm.isTypeAllowed(t), Modifier.weight(1f)) { vm.report(t) } }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                TextButton(onClick = { vm.showExtraTypes = !vm.showExtraTypes }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(if (vm.showExtraTypes) R.string.main_categories else R.string.more_types))
                }
            }
            Spacer(Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ReportCell(type: AlertType, allowed: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val v = type.visual()
    val label = stringResource(v.label)
    Surface(
        onClick = onClick,
        enabled = allowed,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.padding(4.dp).heightIn(min = 104.dp).alpha(if (allowed) 1f else 0.38f)
            .semantics { if (!allowed) contentDescription = "$label" },
        shape = MaterialTheme.shapes.small,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 8.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(64.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(v.icon, null, tint = v.color, modifier = Modifier.size(34.dp)) }
            }
            Spacer(Modifier.size(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2)
            if (!allowed) Text(stringResource(R.string.not_allowed_short), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun CreatedConfirmation(vm: CapViewModel, alert: PersonalAlert) {
    // Auto-close shortly after the last interaction; refining a subtype restarts the timer.
    LaunchedEffect(alert.id, alert.subtype) {
        delay(4_500)
        vm.closeReport()
    }
    val semantic = LocalSemantic.current
    Surface(color = semantic.success, contentColor = semantic.onSuccess, shape = MaterialTheme.shapes.small) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CheckCircle, null)
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.alert_saved, stringResource(alert.type.visual().label)),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = vm::undoLastCreated, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.undo), color = semantic.onSuccess)
            }
        }
    }
    if (alert.type.subtypes.isNotEmpty()) {
        Text(
            stringResource(R.string.refine_optional),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            alert.type.subtypes.forEach { sub ->
                FilterChip(
                    selected = alert.subtype == sub,
                    onClick = { vm.setSubtype(alert.id, if (alert.subtype == sub) null else sub) },
                    label = { Text(stringResource(subtypeLabel(sub))) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}

@Composable
private fun DuplicateDialog(vm: CapViewModel, existing: PersonalAlert) {
    AlertDialog(
        onDismissRequest = vm::dismissDuplicate,
        title = { Text(stringResource(R.string.duplicate_title)) },
        text = { Text(stringResource(R.string.duplicate_text, stringResource(existing.type.visual().label), Format.date(existing.createdAt))) },
        confirmButton = { TextButton(onClick = vm::mergeDuplicate) { Text(stringResource(R.string.merge)) } },
        dismissButton = { TextButton(onClick = vm::createDespiteDuplicate) { Text(stringResource(R.string.create_anyway)) } },
    )
}

@Composable
private fun RoadsideHelpDialog(vm: CapViewModel, alert: PersonalAlert) {
    val context = LocalContext.current
    val lat = String.format(Locale.ROOT, "%.5f", alert.point.lat)
    val lon = String.format(Locale.ROOT, "%.5f", alert.point.lon)
    val smsBody = stringResource(R.string.help_sms, "$lat,$lon", "https://www.openstreetmap.org/?mlat=$lat&mlon=$lon#map=17/$lat/$lon")
    AlertDialog(
        onDismissRequest = { vm.roadsideHelp = null },
        title = { Text(stringResource(R.string.alert_roadside_help)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.roadside_disclaimer), style = MaterialTheme.typography.bodyMedium)
                PrimaryButton(stringResource(R.string.call_emergency), {
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")))
                }, Modifier.fillMaxWidth(), icon = Icons.Outlined.Call)
                SecondaryButton(stringResource(R.string.send_position), {
                    context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).putExtra("sms_body", smsBody))
                }, Modifier.fillMaxWidth(), Icons.Outlined.Sms)
            }
        },
        confirmButton = { TextButton(onClick = { vm.roadsideHelp = null }) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun NoteEditorDialog(vm: CapViewModel, alert: PersonalAlert) {
    var text by remember(alert.id) { mutableStateOf(alert.note.orEmpty()) }
    AlertDialog(
        onDismissRequest = { vm.noteEditor = null },
        title = { Text(stringResource(if (alert.type == AlertType.PLACE) R.string.place_name else R.string.note)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(PersonalAlertRepository.MAX_NOTE_CHARS) },
                supportingText = { Text("${text.length}/${PersonalAlertRepository.MAX_NOTE_CHARS}") },
                minLines = 2,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                vm.setNote(alert.id, text)
                vm.noteEditor = null
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = { vm.noteEditor = null }) { Text(stringResource(R.string.skip)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlertDetailSheet(vm: CapViewModel, alert: PersonalAlert) {
    val semantic = LocalSemantic.current
    ModalBottomSheet(onDismissRequest = { vm.detailAlert = null }) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AlertBadge(alert.type, 52.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(alert.type.visual().label), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.personal_alert_local), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                KeyValue(stringResource(R.string.created_on), Format.date(alert.createdAt))
                KeyValue(stringResource(R.string.passes), alert.passes.toString())
            }
            if (alert.type.subtypes.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                alert.type.subtypes.forEach { sub ->
                    FilterChip(
                        selected = alert.subtype == sub,
                        onClick = { vm.setSubtype(alert.id, if (alert.subtype == sub) null else sub) },
                        label = { Text(stringResource(subtypeLabel(sub))) },
                    )
                }
            }
            alert.note?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(stringResource(R.string.edit_note), { vm.noteEditor = alert }, Modifier.weight(1f), Icons.Outlined.Edit)
                SecondaryButton(stringResource(R.string.delete), { vm.deleteAlerts(setOf(alert.id)) }, Modifier.weight(1f), Icons.Outlined.Delete, color = semantic.danger)
            }
            if (alert.type == AlertType.MAP_ISSUE || alert.type == AlertType.PLACE) {
                SecondaryButton(stringResource(R.string.send_to_osm), { vm.prepareOsmNote(alert) }, Modifier.fillMaxWidth(), Icons.Outlined.Public)
            }
            Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun OsmNoteDialog(vm: CapViewModel, draft: org.capnav.app.data.osm.OsmNotes.Draft) {
    AlertDialog(
        onDismissRequest = vm::cancelOsmNote,
        title = { Text(stringResource(R.string.send_to_osm)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.osm_preview_intro), style = MaterialTheme.typography.bodyMedium)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        "POST https://api.openstreetmap.org/api/0.6/notes\nlat=${draft.point.lat}\nlon=${draft.point.lon}\ntext=${draft.text}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp),
                    )
                }
                Text(stringResource(R.string.osm_public_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = vm::sendOsmNote) { Text(stringResource(R.string.send)) } },
        dismissButton = { TextButton(onClick = vm::cancelOsmNote) { Text(stringResource(R.string.cancel)) } },
    )
}
