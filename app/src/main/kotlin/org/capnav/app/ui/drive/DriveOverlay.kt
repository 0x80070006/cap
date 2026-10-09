package org.capnav.app.ui.drive

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.capnav.app.R
import org.capnav.app.data.settings.AppSettings
import org.capnav.app.model.PoiCategory
import org.capnav.app.navigation.ActiveTrip
import org.capnav.app.navigation.TripState
import org.capnav.app.navigation.activeTrip
import org.capnav.app.ui.CapViewModel
import org.capnav.app.ui.common.AlertBadge
import org.capnav.app.ui.common.Format
import org.capnav.app.ui.common.MapIconButton
import org.capnav.app.ui.common.PrimaryButton
import org.capnav.app.ui.common.SecondaryButton
import org.capnav.app.ui.common.SectionTitle
import org.capnav.app.ui.common.subtypeLabel
import org.capnav.app.ui.common.visual
import org.capnav.app.ui.map.ReportFab
import org.capnav.app.ui.theme.Brand
import org.capnav.app.ui.theme.LocalSemantic

@Composable
fun DriveOverlay(vm: CapViewModel, state: TripState, settings: AppSettings) {
    val trip = state.activeTrip ?: return
    val fix by vm.fix.collectAsStateWithLifecycle()
    var details by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
            when (state) {
                is TripState.Paused -> PausedBanner(vm)
                is TripState.Rerouting -> StatusBanner(stringResource(R.string.rerouting), progress = true)
                is TripState.Error -> StatusBanner(stringResource(R.string.reroute_failed), progress = false, action = stringResource(R.string.retry)) { vm.resume() }
                else -> ManeuverBanner(trip, settings)
            }
            vm.reminder?.let { ReminderBanner(vm, it, settings) }
        }

        Column(
            Modifier.align(if (settings.reportButtonLeft) Alignment.CenterStart else Alignment.CenterEnd).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MapIconButton(Icons.Outlined.Search, stringResource(R.string.add_stop), { vm.openSearch(forStop = true) })
            MapIconButton(
                if (settings.voiceEnabled) Icons.AutoMirrored.Outlined.VolumeUp else Icons.AutoMirrored.Outlined.VolumeOff,
                stringResource(if (settings.voiceEnabled) R.string.mute else R.string.unmute),
                { vm.updateSettings { it.copy(voiceEnabled = !it.voiceEnabled) } },
            )
            if (!vm.followUser) MapIconButton(Icons.Outlined.MyLocation, stringResource(R.string.recenter), vm::recenter)
            org.capnav.app.ui.map.CompassButton(vm)
            ReportFab { vm.openReport() }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Bottom) {
                SpeedBubble(fix?.speedMps ?: 0f, settings)
            }
            vm.stopCountdown?.let { StopCountdownBar(it) { vm.cancelStop() } }
            EtaBar(vm, trip, state is TripState.Paused, settings, details) { details = !details }
            if (details) TripDetails(vm, trip, settings)
        }
    }
    if (vm.addStopOpen) AddStopSheet(vm, settings)
}

@Composable
private fun ManeuverBanner(trip: ActiveTrip, settings: AppSettings) {
    val p = trip.progress
    val m = trip.route.maneuvers.getOrNull(p.maneuverIndex) ?: return
    val then = trip.route.maneuvers.getOrNull(p.maneuverIndex + 1)
    Surface(color = Brand.navy, contentColor = Color.White, shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)) {
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                ManeuverIcon(m.kind, Modifier.size(56.dp), Brand.blue)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(Format.distance(p.distanceToManeuverM, settings.units), fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Text(
                        m.street ?: m.instruction,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (then != null && p.distanceToManeuverM < 1_000) {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.then), style = MaterialTheme.typography.labelLarge, color = Brand.slateLight)
                    Spacer(Modifier.width(8.dp))
                    ManeuverIcon(then.kind, Modifier.size(24.dp), Brand.slateLight)
                    Spacer(Modifier.width(8.dp))
                    Text(then.street ?: then.instruction, style = MaterialTheme.typography.bodyMedium, color = Brand.slateLight, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun StatusBanner(text: String, progress: Boolean, action: String? = null, onAction: () -> Unit = {}) {
    Surface(color = Brand.navy, contentColor = Color.White, shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (progress) CircularProgressIndicator(Modifier.size(28.dp), color = Brand.blue, strokeWidth = 3.dp)
            Spacer(Modifier.width(16.dp))
            Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (action != null) TextButton(onClick = onAction, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(action, color = Brand.blue, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun PausedBanner(vm: CapViewModel) {
    Surface(color = Color(0xFF4A2D99), contentColor = Color.White, shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)) {
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp)) {
            Text(stringResource(R.string.trip_paused), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.trip_paused_hint), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.size(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(stringResource(R.string.resume), { vm.resume() }, Modifier.weight(1f), icon = Icons.Outlined.PlayArrow)
                SecondaryButton(stringResource(R.string.stop), { vm.requestStop() }, Modifier.weight(1f), Icons.Outlined.Close, color = Color.White)
            }
        }
    }
}

@Composable
private fun ReminderBanner(vm: CapViewModel, alert: org.capnav.app.model.PersonalAlert, settings: AppSettings) {
    val label = stringResource(alert.type.visual().label)
    val sub = alert.subtype?.let { stringResource(subtypeLabel(it)) }
    // Full opacity while reminding; the map marker itself keeps its reduced opacity.
    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(2.dp, alert.type.visual().color),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.padding(12.dp).fillMaxWidth().semantics { liveRegion = LiveRegionMode.Assertive },
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AlertBadge(alert.type)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(sub ?: label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.reported_by_you, Format.date(alert.createdAt), Format.distance(vm.reminderDistanceM, settings.units)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(stringResource(R.string.ok), vm::dismissReminder, Modifier.weight(1f))
                SecondaryButton(
                    stringResource(R.string.delete_this_alert), { vm.deleteAlerts(setOf(alert.id)) },
                    Modifier.weight(1.4f), color = LocalSemantic.current.danger,
                )
            }
        }
    }
}

@Composable
private fun SpeedBubble(speedMps: Float, settings: AppSettings) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.size(76.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("${Format.speed(speedMps, settings.units)}", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(Format.speedUnit(settings.units), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun StopCountdownBar(seconds: Int, onCancel: () -> Unit) {
    Surface(color = LocalSemantic.current.danger, contentColor = LocalSemantic.current.onDanger) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.stopping_in, seconds), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.cancel), color = LocalSemantic.current.onDanger, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun EtaBar(vm: CapViewModel, trip: ActiveTrip, paused: Boolean, settings: AppSettings, expanded: Boolean, onToggle: () -> Unit) {
    val p = trip.progress
    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().then(if (!expanded) Modifier.navigationBarsPadding() else Modifier).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarButton(Icons.Outlined.Close, stringResource(R.string.stop), LocalSemantic.current.danger) { vm.requestStop() }
            Column(
                Modifier.weight(1f).clickable(onClick = onToggle).padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(Format.duration(p.remainingS), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                val eta = Format.clock(System.currentTimeMillis() + (p.remainingS * 1000).toLong())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.distance_eta, Format.distance(p.remainingM, settings.units), eta),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(if (expanded) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess, stringResource(R.string.details), Modifier.size(20.dp))
                }
            }
            BarButton(Icons.Outlined.Add, stringResource(R.string.add_stop), Brand.purple) { vm.openAddStop() }
            Spacer(Modifier.width(8.dp))
            if (paused) BarButton(Icons.Outlined.PlayArrow, stringResource(R.string.resume), Brand.purple) { vm.resume() }
            else BarButton(Icons.Outlined.Pause, stringResource(R.string.pause), Brand.purple) { vm.pause() }
        }
    }
}

@Composable
private fun BarButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, color),
        modifier = Modifier.size(56.dp),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, label, tint = color, modifier = Modifier.size(28.dp)) }
    }
}

@Composable
private fun TripDetails(vm: CapViewModel, trip: ActiveTrip, settings: AppSettings) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp)) {
            HorizontalDivider()
            SectionTitle(stringResource(R.string.stops))
            val remaining = trip.remainingWaypoints
            remaining.forEachIndexed { i, w ->
                val isDest = i == remaining.lastIndex
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).background(if (isDest) Brand.navy else Brand.purple, CircleShape))
                    Spacer(Modifier.width(12.dp))
                    Text(w.place.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!isDest) {
                        IconButton(onClick = { vm.moveWaypoint(w.id, 1) }, enabled = i < remaining.lastIndex - 1) {
                            Icon(Icons.Outlined.ExpandMore, stringResource(R.string.move_down))
                        }
                        IconButton(onClick = { vm.removeWaypoint(w.id) }) { Icon(Icons.Outlined.Close, stringResource(R.string.remove)) }
                    }
                }
            }
            if (remaining.size > 1) {
                SecondaryButton(stringResource(R.string.skip_next_stop), { vm.skipWaypoint() }, Modifier.fillMaxWidth(), Icons.Outlined.SkipNext)
            }
            Spacer(Modifier.size(12.dp))
            Text(
                stringResource(R.string.trip_started_at, Format.clock(trip.startedAtMs), Format.distance(trip.drivenM, settings.units)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddStopSheet(vm: CapViewModel, settings: AppSettings) {
    ModalBottomSheet(onDismissRequest = vm::closeAddStop, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding()) {
            Text(stringResource(R.string.add_stop), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.add_stop_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PoiCategory.entries.forEach { c ->
                    FilterChip(
                        selected = vm.addStopCategory == c,
                        onClick = { vm.searchAlongRoute(c) },
                        label = { Text(stringResource(poiLabel(c))) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
            SecondaryButton(
                stringResource(R.string.search_address), { vm.closeAddStop(); vm.openSearch(forStop = true) },
                Modifier.fillMaxWidth().padding(vertical = 8.dp), Icons.Outlined.Search,
            )
            when {
                vm.addStopLoading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                vm.addStopError -> Text(stringResource(R.string.search_error), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                vm.addStopCategory != null && vm.addStopResults.isEmpty() ->
                    Text(stringResource(R.string.no_results_along_route), modifier = Modifier.padding(16.dp))
            }
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(vm.addStopResults, key = { "${it.place.point.lat},${it.place.point.lon}" }) { poi ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.addStop(poi.place) }.heightIn(min = 64.dp).padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(poi.place.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (poi.place.detail.isNotEmpty()) Text(
                                poi.place.detail, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            stringResource(R.string.detour_min, ((poi.detourS + 30) / 60).toInt()),
                            style = MaterialTheme.typography.titleSmall,
                            color = Brand.purple,
                        )
                    }
                    HorizontalDivider()
                }
            }
            Spacer(Modifier.size(16.dp))
        }
    }
}

fun poiLabel(c: PoiCategory) = when (c) {
    PoiCategory.FUEL -> R.string.poi_fuel
    PoiCategory.CHARGING -> R.string.poi_charging
    PoiCategory.PARKING -> R.string.poi_parking
    PoiCategory.CAFE -> R.string.poi_cafe
    PoiCategory.TOILETS -> R.string.poi_toilets
    PoiCategory.PHARMACY -> R.string.poi_pharmacy
    PoiCategory.RESTAURANT -> R.string.poi_restaurant
}
