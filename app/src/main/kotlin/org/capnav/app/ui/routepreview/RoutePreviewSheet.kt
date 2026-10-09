package org.capnav.app.ui.routepreview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.capnav.app.R
import org.capnav.app.data.settings.Units
import org.capnav.app.model.Route
import org.capnav.app.model.RouteLabel
import org.capnav.app.model.RouteOptions
import org.capnav.app.model.VehicleProfile
import org.capnav.app.navigation.TripState
import org.capnav.app.ui.CapViewModel
import org.capnav.app.ui.common.Format
import org.capnav.app.ui.common.MapIconButton
import org.capnav.app.ui.common.Panel
import org.capnav.app.ui.common.PrimaryButton
import org.capnav.app.ui.common.SecondaryButton
import org.capnav.app.ui.common.SectionTitle
import org.capnav.app.ui.common.SheetHandle
import org.capnav.app.ui.drive.ManeuverIcon
import org.capnav.app.ui.theme.Brand

private enum class Level { COLLAPSED, HALF, EXPANDED }

@Composable
fun RoutePreviewSheet(vm: CapViewModel, s: TripState.Previewing, units: Units, modifier: Modifier = Modifier) {
    var level by remember { mutableStateOf(Level.HALF) }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.72f).dp
    val route = s.selectedRoute
    val fastest = s.routes.minOf { it.durationS }

    Panel(modifier.navigationBarsPadding()) {
        val drag = rememberDraggableState { delta ->
            level = when {
                delta < -12 && level != Level.EXPANDED -> Level.entries[level.ordinal + 1]
                delta > 12 && level != Level.COLLAPSED -> Level.entries[level.ordinal - 1]
                else -> level
            }
        }
        SheetHandle(
            Modifier.draggable(drag, Orientation.Vertical)
                .semantics { role = Role.Button },
        )
        if (vm.computingRoute || s.loading) LinearProgressIndicator(Modifier.fillMaxWidth())

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Format.duration(route.durationS), style = MaterialTheme.typography.headlineMedium)
                val eta = Format.clock(System.currentTimeMillis() + (route.durationS * 1000).toLong())
                Text(
                    stringResource(R.string.distance_eta, Format.distance(route.lengthM, units), eta),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                RouteTags(route)
            }
            MapIconButton(Icons.Outlined.Close, stringResource(R.string.close), { vm.cancelPreview() }, size = 48.dp)
        }
        Text(
            stringResource(R.string.traffic_unavailable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        Column(Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState())) {
            if (level != Level.COLLAPSED && s.routes.size > 1) {
                SectionTitle(stringResource(R.string.alternatives))
                s.routes.forEachIndexed { i, r ->
                    AlternativeRow(r, i == s.selected, r.durationS - fastest, units) { vm.selectRoute(i) }
                    Spacer(Modifier.size(8.dp))
                }
            }
            if (level == Level.EXPANDED) {
                SectionTitle(stringResource(R.string.stops))
                s.waypoints.forEachIndexed { i, w ->
                    val isDest = i == s.waypoints.lastIndex
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (isDest) stringResource(R.string.destination) else stringResource(R.string.stop_n, i + 1),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isDest) MaterialTheme.colorScheme.onSurfaceVariant else Brand.purple,
                            modifier = Modifier.width(84.dp),
                        )
                        Text(w.place.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!isDest) {
                            IconButton(onClick = { vm.moveWaypoint(w.id, -1) }, enabled = i > 0) {
                                Icon(Icons.Outlined.ArrowUpward, stringResource(R.string.move_up))
                            }
                            IconButton(onClick = { vm.moveWaypoint(w.id, 1) }, enabled = i < s.waypoints.lastIndex - 1) {
                                Icon(Icons.Outlined.ArrowDownward, stringResource(R.string.move_down))
                            }
                            IconButton(onClick = { vm.removeWaypoint(w.id) }) {
                                Icon(Icons.Outlined.Close, stringResource(R.string.remove))
                            }
                        }
                    }
                }
                SecondaryButton(stringResource(R.string.add_stop), { vm.openSearch(forStop = true) }, Modifier.fillMaxWidth(), Icons.Outlined.Add)

                SectionTitle(stringResource(R.string.route_options))
                OptionsEditor(s.options) { vm.updateRouteOptions(it) }

                SectionTitle(stringResource(R.string.maneuvers))
                route.maneuvers.forEach { m ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        ManeuverIcon(m.kind, Modifier.size(28.dp), MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(12.dp))
                        Text(m.instruction, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        if (m.lengthM > 0) Text(
                            Format.distance(m.lengthM, units), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(
                stringResource(if (level == Level.EXPANDED) R.string.less else R.string.details),
                { level = if (level == Level.EXPANDED) Level.HALF else Level.EXPANDED },
                Modifier.weight(1f),
            )
            PrimaryButton(stringResource(R.string.start), { vm.startTrip() }, Modifier.weight(1.4f), icon = Icons.Outlined.Navigation)
        }
    }
}

@Composable
private fun RouteTags(r: Route) {
    val tags = buildList {
        if (r.hasToll) add(R.string.tag_toll)
        if (r.hasHighway) add(R.string.tag_highway)
        if (r.hasFerry) add(R.string.tag_ferry)
    }
    if (tags.isEmpty()) return
    Text(
        tags.map { stringResource(it) }.joinToString(" · "),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AlternativeRow(r: Route, selected: Boolean, deltaS: Double, units: Units, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Brand.purple else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (r.label == RouteLabel.FASTEST) R.string.fastest else R.string.alternative),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${Format.duration(r.durationS)} · ${Format.distance(r.lengthM, units)}",
                    style = MaterialTheme.typography.titleMedium,
                )
                RouteTags(r)
            }
            if (deltaS >= 60) Text(
                "+${Format.duration(deltaS)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun OptionsEditor(o: RouteOptions, onChange: (RouteOptions) -> Unit) {
    OptionSwitch(stringResource(R.string.avoid_tolls), o.avoidTolls) { onChange(o.copy(avoidTolls = it)) }
    OptionSwitch(stringResource(R.string.avoid_highways), o.avoidHighways) { onChange(o.copy(avoidHighways = it)) }
    OptionSwitch(stringResource(R.string.avoid_ferries), o.avoidFerries) { onChange(o.copy(avoidFerries = it)) }
    OptionSwitch(stringResource(R.string.avoid_unpaved), o.avoidUnpaved) { onChange(o.copy(avoidUnpaved = it)) }
    Spacer(Modifier.size(8.dp))
    Text(stringResource(R.string.vehicle), style = MaterialTheme.typography.labelMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        VehicleProfile.entries.forEach { v ->
            FilterChip(
                selected = o.vehicle == v,
                onClick = { onChange(o.copy(vehicle = v)) },
                label = { Text(stringResource(vehicleLabel(v))) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

fun vehicleLabel(v: VehicleProfile) = when (v) {
    VehicleProfile.CAR -> R.string.vehicle_car
    VehicleProfile.MOTORCYCLE -> R.string.vehicle_moto
    VehicleProfile.TRUCK -> R.string.vehicle_truck
    VehicleProfile.BICYCLE -> R.string.vehicle_bike
    VehicleProfile.PEDESTRIAN -> R.string.vehicle_walk
}

@Composable
private fun OptionSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
