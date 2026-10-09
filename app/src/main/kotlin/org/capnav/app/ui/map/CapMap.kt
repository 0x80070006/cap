package org.capnav.app.ui.map

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.capnav.app.model.AlertId
import org.capnav.app.model.AlertType
import org.capnav.app.model.GeoPoint
import org.capnav.app.model.PersonalAlert
import org.capnav.app.model.Route
import org.capnav.app.navigation.Fix
import org.capnav.app.ui.CameraCommand
import org.capnav.app.ui.common.visual
import org.maplibre.android.maps.MapView

data class MapContent(
    val routes: List<Route> = emptyList(),
    val selectedRoute: Int = 0,
    val waypoints: List<Pair<GeoPoint, Boolean>> = emptyList(),
    val alerts: List<PersonalAlert> = emptyList(),
    val alertOpacity: Float = 0.45f,
    val selection: GeoPoint? = null,
)

@Composable
fun CapMap(
    styleUrl: String,
    content: MapContent,
    fix: Fix?,
    camera: CameraCommand?,
    followMode: FollowMode,
    headingUp: Boolean,
    dark: Boolean,
    fitKey: Any?,
    fitPoints: List<GeoPoint>,
    onAlertClick: (AlertId) -> Unit,
    onLongPress: (GeoPoint) -> Unit,
    onUserGesture: () -> Unit,
    onBearingChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current
    val icons = rememberAlertIcons()
    val mapView = remember { MapView(context).apply { onCreate(null) } }
    var controller by remember { mutableStateOf<MapController?>(null) }
    val alertClick by rememberUpdatedState(onAlertClick)
    val longPress by rememberUpdatedState(onLongPress)
    val gesture by rememberUpdatedState(onUserGesture)
    val bearingChange by rememberUpdatedState(onBearingChange)

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { map ->
            controller = MapController(map).apply {
                this.onAlertClick = { alertClick(it) }
                this.onLongPress = { longPress(it) }
                this.onUserGesture = { gesture() }
                this.onBearingChange = { bearingChange(it) }
            }
        }
    }

    val ctl = controller
    if (ctl != null) {
        LaunchedEffect(ctl, icons) { ctl.setIcons(icons) }
        LaunchedEffect(ctl, styleUrl) { ctl.setStyle(styleUrl, dark) }
        LaunchedEffect(ctl, content.alerts, content.alertOpacity) { ctl.setAlerts(content.alerts, content.alertOpacity) }
        LaunchedEffect(ctl, content.routes, content.selectedRoute) { ctl.setRoutes(content.routes, content.selectedRoute) }
        LaunchedEffect(ctl, content.waypoints) { ctl.setWaypoints(content.waypoints) }
        LaunchedEffect(ctl, content.selection) { ctl.setSelection(content.selection) }
        LaunchedEffect(ctl, fix) {
            ctl.setLocation(fix)
            when {
                fix == null -> Unit
                followMode == FollowMode.DRIVE -> ctl.follow(fix, headingUp, viewHeightPx = mapView.height)
                followMode == FollowMode.BROWSE -> ctl.followBrowse(fix, headingUp)
                else -> Unit
            }
        }
        LaunchedEffect(ctl, camera) { camera?.let { ctl.moveTo(it.point, it.zoom, it.bearing) } }
        LaunchedEffect(ctl, fitKey) {
            if (fitKey != null) {
                val pad = with(density) { 48.dp.roundToPx() }
                ctl.fit(fitPoints, pad, with(density) { 120.dp.roundToPx() }, pad, mapView.height / 2)
            }
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

enum class FollowMode { NONE, BROWSE, DRIVE }

/** Personal alert markers: white disc, dashed ring and pictogram in the type colour. */
@Composable
fun rememberAlertIcons(): Map<AlertType, Bitmap> {
    val density = LocalDensity.current
    val painters = AlertType.entries.map { rememberVectorPainter(it.visual().icon) }
    return remember(density) {
        AlertType.entries.mapIndexed { i, t -> t to renderMarker(painters[i], t.visual().color, density) }.toMap()
    }
}

private fun renderMarker(painter: VectorPainter, color: Color, density: Density): Bitmap {
    val sizePx = with(density) { 38.dp.toPx() }
    val img = ImageBitmap(sizePx.toInt(), sizePx.toInt())
    val stroke = with(density) { 2.5.dp.toPx() }
    val dash = with(density) { 4.dp.toPx() }
    val iconPx = sizePx * 0.56f
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(img), Size(sizePx, sizePx)) {
        val r = size.minDimension / 2 - stroke
        drawCircle(Color.White, radius = r, center = Offset(size.width / 2, size.height / 2))
        drawCircle(
            color = color,
            radius = r,
            center = Offset(size.width / 2, size.height / 2),
            style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash * 0.7f))),
        )
        val inset = (sizePx - iconPx) / 2
        translate(inset, inset) {
            with(painter) { draw(Size(iconPx, iconPx), colorFilter = ColorFilter.tint(color)) }
        }
    }
    return img.asAndroidBitmap()
}
