package org.capnav.app.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAlert
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Edit
import org.capnav.app.ui.favorites.vector
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.capnav.app.R
import org.capnav.app.data.places.FavoriteKind
import org.capnav.app.model.Place
import org.capnav.app.navigation.Geo
import org.capnav.app.navigation.TripState
import org.capnav.app.navigation.activeTrip
import org.capnav.app.ui.CapViewModel
import org.capnav.app.ui.Screen
import org.capnav.app.ui.alerts.AlertSheets
import org.capnav.app.ui.common.Format
import org.capnav.app.ui.common.MapIconButton
import org.capnav.app.ui.common.Panel
import org.capnav.app.ui.common.PrimaryButton
import org.capnav.app.ui.common.SecondaryButton
import org.capnav.app.ui.common.SheetHandle
import org.capnav.app.ui.drive.DriveOverlay
import org.capnav.app.ui.drive.TripSummaryScreen
import org.capnav.app.ui.routepreview.RoutePreviewSheet
import org.capnav.app.ui.theme.Brand

@Composable
fun MainMapScreen(vm: CapViewModel, dark: Boolean, onRequestPermissions: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val trip by vm.trip.collectAsStateWithLifecycle()
    val alerts by vm.alerts.collectAsStateWithLifecycle()
    val fix by vm.fix.collectAsStateWithLifecycle()
    val active = trip.activeTrip

    val detourRoute = vm.detour?.route
    val content = remember(trip, alerts, settings, vm.selectedPlace, detourRoute) {
        val visibleAlerts = if (!settings.alertsVisible) emptyList()
        else alerts.filter { it.type !in settings.hiddenAlertTypes }
        when (val s = trip) {
            is TripState.Previewing -> MapContent(
                routes = s.routes, selectedRoute = s.selected,
                waypoints = s.waypoints.mapIndexed { i, w -> w.place.point to (i == s.waypoints.lastIndex) },
                alerts = visibleAlerts, alertOpacity = settings.alertOpacity,
            )
            else -> if (active != null) MapContent(
                routes = listOfNotNull(active.route, detourRoute),
                waypoints = active.remainingWaypoints.let { l -> l.mapIndexed { i, w -> w.place.point to (i == l.lastIndex) } },
                alerts = visibleAlerts, alertOpacity = settings.alertOpacity,
            ) else MapContent(alerts = visibleAlerts, alertOpacity = settings.alertOpacity, selection = vm.selectedPlace?.point)
        }
    }
    val preview = trip as? TripState.Previewing
    val fitPoints = remember(preview?.routes) {
        preview?.routes?.flatMap { r -> r.shape.filterIndexed { i, _ -> i % 5 == 0 } + r.shape.last() }.orEmpty()
    }

    Box(Modifier.fillMaxSize()) {
        CapMap(
            styleUrl = if (dark) settings.styleNightUrl else settings.styleDayUrl,
            content = content,
            // While navigating, draw the cursor on the matched road position when the fix is close to it.
            fix = fix?.let { f ->
                val matched = (trip as? TripState.Navigating)?.trip?.progress?.matched
                if (matched != null && Geo.distanceM(f.point, matched) < 35.0) f.copy(point = matched) else f
            },
            camera = vm.camera,
            followMode = when {
                !vm.followUser || preview != null -> FollowMode.NONE
                active != null -> FollowMode.DRIVE
                else -> FollowMode.BROWSE
            },
            headingUp = vm.headingUp,
            dark = dark,
            navZoom = settings.navZoom.toDouble(),
            navTilt = settings.navTilt.toDouble(),
            fitKey = preview?.routes,
            fitPoints = fitPoints,
            onAlertClick = { vm.detailAlert = it },
            onLongPress = { p -> if (active == null) vm.onMapLongPress(p) else vm.openReport(p) },
            onUserGesture = { vm.followUser = false },
            onBearingChange = { vm.mapBearing = it },
            onUserZoom = vm::onUserZoom,
            modifier = Modifier.fillMaxSize(),
        )

        when (val s = trip) {
            is TripState.Previewing -> RoutePreviewSheet(vm, s, settings.units, Modifier.align(Alignment.BottomCenter))
            is TripState.Finished -> TripSummaryScreen(vm, s, settings.units)
            else -> if (active != null) DriveOverlay(vm, trip, settings) else IdleOverlay(vm)
        }
        if (trip !is TripState.Finished) {
            LocationBanner(vm, onRequestPermissions, Modifier.align(if (active != null) Alignment.Center else Alignment.TopCenter))
        }
        AlertSheets(vm)
    }
}

@Composable
private fun IdleOverlay(vm: CapViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MapIconButton(Icons.Outlined.Settings, stringResource(R.string.settings), vm::openSettings)
            MapIconButton(
                if (settings.voiceEnabled) Icons.AutoMirrored.Outlined.VolumeUp else Icons.AutoMirrored.Outlined.VolumeOff,
                stringResource(if (settings.voiceEnabled) R.string.mute else R.string.unmute),
                { vm.updateSettings { it.copy(voiceEnabled = !it.voiceEnabled) } },
            )
            MapIconButton(Icons.Outlined.TaskAlt, stringResource(R.string.my_alerts), { vm.screen = Screen.MY_ALERTS })
            MapIconButton(
                if (settings.alertsVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                stringResource(if (settings.alertsVisible) R.string.hide_alerts else R.string.show_alerts),
                { vm.updateSettings { it.copy(alertsVisible = !it.alertsVisible) } },
            )
            CompassButton(vm)
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                val report = @Composable { ReportFab { vm.openReport() } }
                val recenter = @Composable {
                    MapIconButton(Icons.Outlined.MyLocation, stringResource(R.string.recenter), vm::recenter)
                }
                if (settings.reportButtonLeft) { report(); recenter() } else { recenter(); report() }
            }
            val place = vm.selectedPlace
            if (place != null) PlaceCard(vm, place) else HomePanel(vm)
        }
    }
}

/**
 * Recalibration: the needle shows where north is; a tap turns the map so the direction of travel
 * points up and re-centres on the user, a long press returns to north-up.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompassButton(vm: CapViewModel) {
    val label = stringResource(R.string.reorient)
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (vm.headingUp && vm.followUser) 2.dp else 1.dp,
            if (vm.headingUp && vm.followUser) Brand.blueDeep else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.size(56.dp)
            .clip(CircleShape)
            .combinedClickable(onClickLabel = label, onLongClickLabel = stringResource(R.string.north_up), onClick = vm::reorient, onLongClick = vm::northUp)
            .semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Outlined.Navigation, null,
                tint = Brand.blueDeep,
                modifier = Modifier.size(28.dp).rotate(-vm.mapBearing.toFloat()),
            )
        }
    }
}

@Composable
fun ReportFab(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(64.dp),
        shape = CircleShape,
        color = Brand.blue,
        contentColor = Brand.navy,
        border = BorderStroke(2.dp, Brand.blueDeep),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.AddAlert, stringResource(R.string.report), Modifier.size(30.dp))
        }
    }
}

/** Makes sure navigation runs on the phone's precise (GNSS) position, and says how to fix it if not. */
@Composable
private fun LocationBanner(vm: CapViewModel, onRequestPermissions: () -> Unit, modifier: Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val (text, action) = when {
        !vm.locationGranted -> R.string.location_missing to { onRequestPermissions() }
        !vm.locationPrecise -> R.string.location_approximate to {
            context.startActivity(
                android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(android.net.Uri.fromParts("package", context.packageName, null)),
            )
        }
        !vm.gpsEnabled -> R.string.gps_off to {
            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }
        else -> return
    }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.statusBarsPadding().padding(start = 12.dp, end = 84.dp, top = 12.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.LocationOff, null)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
            androidx.compose.material3.TextButton(onClick = action, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.fix_it))
            }
        }
    }
}

@Composable
private fun HomePanel(vm: CapViewModel) {
    val places by vm.places.collectAsStateWithLifecycle()
    Panel(Modifier.navigationBarsPadding()) {
        SheetHandle()
        Surface(
            onClick = { vm.openSearch() },
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.where_to),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ShortcutChip(places.home?.icon?.vector() ?: Icons.Outlined.Home, stringResource(R.string.home), places.home?.place, vm)
            ShortcutChip(places.work?.icon?.vector() ?: Icons.Outlined.Work, stringResource(R.string.work), places.work?.place, vm)
            places.favorites.filter { it.kind == FavoriteKind.CUSTOM }.take(8).forEach {
                ShortcutChip(it.icon.vector(), it.displayName, it.place, vm)
            }
            AssistChip(
                onClick = { vm.screen = Screen.FAVORITES },
                label = { Text(stringResource(R.string.manage_favorites)) },
                leadingIcon = { Icon(Icons.Outlined.Edit, null, Modifier.size(AssistChipDefaults.IconSize)) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
        places.recents.take(3).forEach { p ->
            Row(
                Modifier.fillMaxWidth().clickable { vm.routeTo(p) }.heightIn(min = 52.dp).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (p.detail.isNotEmpty()) Text(
                        p.detail, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShortcutChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, place: Place?, vm: CapViewModel) {
    AssistChip(
        onClick = { if (place != null) vm.routeTo(place) else vm.screen = Screen.FAVORITES },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(icon, null, Modifier.size(AssistChipDefaults.IconSize)) },
        modifier = Modifier.heightIn(min = 48.dp),
    )
}

@Composable
private fun PlaceCard(vm: CapViewModel, place: Place) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val fix by vm.fix.collectAsStateWithLifecycle()
    Panel(Modifier.navigationBarsPadding()) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(top = 8.dp)) {
                Text(place.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val dist = fix?.let { Format.distance(Geo.distanceM(it.point, place.point), settings.units) }
                val detail = listOfNotNull(dist, place.detail.ifEmpty { null }).joinToString(" · ")
                if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            MapIconButton(Icons.Outlined.Close, stringResource(R.string.close), { vm.selectedPlace = null }, size = 48.dp)
        }
        Spacer(Modifier.size(12.dp))
        PrimaryButton(
            stringResource(if (vm.computingRoute) R.string.computing_route else R.string.directions),
            { vm.routeTo(place) },
            Modifier.fillMaxWidth(),
            enabled = !vm.computingRoute,
            icon = Icons.Outlined.Directions,
        )
        Spacer(Modifier.size(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SecondaryButton(stringResource(R.string.report_here), { vm.openReport(place.point) }, Modifier.weight(1f), Icons.Outlined.AddAlert)
            SecondaryButton(stringResource(R.string.favorite), { vm.openFavoriteEditor(place) }, Modifier.weight(1f), Icons.Outlined.Star)
        }
        Spacer(Modifier.size(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            SecondaryButton(stringResource(R.string.set_home), { vm.setFavorite(FavoriteKind.HOME, place) }, Modifier.weight(1f), Icons.Outlined.Home)
            SecondaryButton(stringResource(R.string.set_work), { vm.setFavorite(FavoriteKind.WORK, place) }, Modifier.weight(1f), Icons.Outlined.Work)
        }
    }
}
