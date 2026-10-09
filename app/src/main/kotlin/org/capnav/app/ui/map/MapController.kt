package org.capnav.app.ui.map

import android.graphics.Bitmap
import android.graphics.Color as AColor
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.capnav.app.model.AlertId
import org.capnav.app.model.AlertType
import org.capnav.app.model.GeoPoint
import org.capnav.app.model.PersonalAlert
import org.capnav.app.model.Route
import org.capnav.app.navigation.Fix
import kotlin.math.max

/**
 * Imperative bridge to MapLibre. Holds the last data for every layer so it can be re-applied after
 * a style switch (day/night). Z-order (bottom → top): personal alerts, alternatives, route,
 * waypoints, selection, user position — so alerts never hide the route.
 */
class MapController(private val map: MapLibreMap) {
    private var style: Style? = null
    private var icons: Map<AlertType, Bitmap> = emptyMap()
    private var alerts: List<PersonalAlert> = emptyList()
    private var alertOpacity = 0.45f
    private var routes: List<Route> = emptyList()
    private var selected = 0
    private var waypoints: List<Pair<GeoPoint, Boolean>> = emptyList()
    private var selection: GeoPoint? = null
    private var fix: Fix? = null
    private var lastCamera: CameraPosition? = null

    var onAlertClick: (AlertId) -> Unit = {}
    var onLongPress: (GeoPoint) -> Unit = {}
    var onUserGesture: () -> Unit = {}
    var onBearingChange: (Double) -> Unit = {}
    var onUserZoom: (Double) -> Unit = {}
    private var dark = false

    init {
        map.uiSettings.isCompassEnabled = false
        map.uiSettings.isLogoEnabled = false
        // ODbL attribution must stay visible: top-left, below the status bar, clear of the panels.
        map.uiSettings.isAttributionEnabled = true
        map.uiSettings.attributionGravity = android.view.Gravity.TOP or android.view.Gravity.START
        map.uiSettings.setAttributionMargins(24, 150, 0, 0)
        map.addOnMapLongClickListener { ll ->
            onLongPress(GeoPoint(ll.latitude, ll.longitude)); true
        }
        map.addOnMapClickListener { ll -> handleClick(ll) }
        map.addOnCameraMoveListener { onBearingChange(map.cameraPosition.bearing) }
        map.addOnRotateListener(object : MapLibreMap.OnRotateListener {
            override fun onRotateBegin(detector: org.maplibre.android.gestures.RotateGestureDetector) = onUserGesture()
            override fun onRotate(detector: org.maplibre.android.gestures.RotateGestureDetector) = Unit
            override fun onRotateEnd(detector: org.maplibre.android.gestures.RotateGestureDetector) = Unit
        })
        map.addOnScaleListener(object : MapLibreMap.OnScaleListener {
            override fun onScaleBegin(detector: org.maplibre.android.gestures.StandardScaleGestureDetector) {
                scaling = true
            }
            override fun onScale(detector: org.maplibre.android.gestures.StandardScaleGestureDetector) = Unit
            override fun onScaleEnd(detector: org.maplibre.android.gestures.StandardScaleGestureDetector) {
                scaling = false
                onUserZoom(map.cameraPosition.zoom)
            }
        })
        map.addOnMoveListener(object : MapLibreMap.OnMoveListener {
            override fun onMoveBegin(detector: org.maplibre.android.gestures.MoveGestureDetector) = onUserGesture()
            override fun onMove(detector: org.maplibre.android.gestures.MoveGestureDetector) = Unit
            override fun onMoveEnd(detector: org.maplibre.android.gestures.MoveGestureDetector) = Unit
        })
    }

    fun setStyle(url: String, dark: Boolean) {
        if (style?.uri == url) return
        this.dark = dark
        style = null
        map.setStyle(Style.Builder().fromUri(url)) { s ->
            style = s
            recolorBaseMap(s)
            install(s)
            applyAll()
            // Loading a style resets the camera to the style default; restore ours.
            lastCamera?.let { map.moveCamera(CameraUpdateFactory.newCameraPosition(it)) }
        }
    }

    /**
     * Base-map palette requested for readability: water in blue, green spaces in green, buildings in
     * grey. Applied on top of the OpenFreeMap styles so any OpenMapTiles-schema style works.
     */
    private fun recolorBaseMap(s: Style) {
        val water = AColor.parseColor(if (dark) "#1D4F7C" else "#9FD4F5")
        val waterLine = AColor.parseColor(if (dark) "#2A6496" else "#7CC2EE")
        val green = AColor.parseColor(if (dark) "#1F4A32" else "#BFE6B3")
        val wood = AColor.parseColor(if (dark) "#1A4029" else "#A6D99A")
        val building = AColor.parseColor(if (dark) "#3A4653" else "#CDD1D6")
        val buildingEdge = AColor.parseColor(if (dark) "#4B5866" else "#B3B9C0")
        s.layers.forEach { layer ->
            val id = layer.id
            when {
                layer is FillLayer && id == "water" -> layer.setProperties(PropertyFactory.fillColor(water))
                layer is LineLayer && id.startsWith("waterway") -> layer.setProperties(PropertyFactory.lineColor(waterLine))
                layer is FillLayer && (id == "park" || id == "landuse_park") ->
                    layer.setProperties(PropertyFactory.fillColor(green), PropertyFactory.fillOpacity(1f))
                layer is FillLayer && id == "landcover_wood" -> layer.setProperties(PropertyFactory.visibility(Property.NONE))
                layer is FillLayer && id.startsWith("building") ->
                    layer.setProperties(PropertyFactory.fillColor(building), PropertyFactory.fillOutlineColor(buildingEdge))
            }
        }
        // Grass and woods from the landcover layer, drawn just under the water so rivers stay on top.
        if (s.getSource("openmaptiles") != null) {
            val greenLayer = FillLayer(LYR_GREEN, "openmaptiles").apply {
                sourceLayer = "landcover"
                setFilter(
                    Expression.match(
                        Expression.get("class"), Expression.literal(false),
                        Expression.stop("grass", true), Expression.stop("wood", true),
                    ),
                )
                setProperties(
                    PropertyFactory.fillColor(
                        Expression.match(Expression.get("class"), Expression.color(green), Expression.stop("wood", Expression.color(wood))),
                    ),
                    PropertyFactory.fillOpacity(0.9f),
                )
            }
            if (s.getLayer("water") != null) s.addLayerBelow(greenLayer, "water") else s.addLayer(greenLayer)
        }
    }

    private fun install(s: Style) {
        icons.forEach { (t, b) -> s.addImage(iconName(t), b) }
        s.addImage(CURSOR, cursorBitmap())
        s.addSource(GeoJsonSource(SRC_ALERTS, FeatureCollection.fromFeatures(emptyList()),
            GeoJsonOptions().withCluster(true).withClusterMaxZoom(13).withClusterRadius(45)))
        s.addSource(GeoJsonSource(SRC_ALT))
        s.addSource(GeoJsonSource(SRC_ROUTE))
        s.addSource(GeoJsonSource(SRC_WAYPOINTS))
        s.addSource(GeoJsonSource(SRC_SELECTION))
        s.addSource(GeoJsonSource(SRC_LOCATION))

        val isCluster = Expression.has("point_count")
        s.addLayer(CircleLayer(LYR_CLUSTERS, SRC_ALERTS).withFilter(isCluster).withProperties(
            PropertyFactory.circleColor(AColor.parseColor("#5A6E82")),
            PropertyFactory.circleRadius(16f),
            PropertyFactory.circleStrokeColor(AColor.WHITE),
            PropertyFactory.circleStrokeWidth(2f),
        ))
        s.addLayer(SymbolLayer(LYR_CLUSTER_COUNT, SRC_ALERTS).withFilter(isCluster).withProperties(
            PropertyFactory.textField(Expression.toString(Expression.get("point_count_abbreviated"))),
            PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
            PropertyFactory.textSize(13f),
            PropertyFactory.textColor(AColor.WHITE),
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.textIgnorePlacement(true),
        ))
        s.addLayer(SymbolLayer(LYR_ALERTS, SRC_ALERTS).withFilter(Expression.not(isCluster)).withProperties(
            PropertyFactory.iconImage(Expression.get("icon")),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
        ))
        s.addLayer(LineLayer(LYR_ALT, SRC_ALT).withProperties(
            PropertyFactory.lineColor(AColor.parseColor("#8FA3B8")),
            PropertyFactory.lineWidth(6f),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        ))
        s.addLayer(LineLayer(LYR_ROUTE_CASING, SRC_ROUTE).withProperties(
            PropertyFactory.lineColor(AColor.parseColor("#5B2DB8")),
            PropertyFactory.lineWidth(11f),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        ))
        s.addLayer(LineLayer(LYR_ROUTE, SRC_ROUTE).withProperties(
            PropertyFactory.lineColor(AColor.parseColor("#8F5BFF")),
            PropertyFactory.lineWidth(7f),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        ))
        s.addLayer(CircleLayer(LYR_WAYPOINTS, SRC_WAYPOINTS).withProperties(
            PropertyFactory.circleRadius(8f),
            PropertyFactory.circleColor(
                Expression.switchCase(Expression.get("dest"), Expression.color(AColor.parseColor("#0B1F33")),
                    Expression.color(AColor.parseColor("#0A6CC4"))),
            ),
            PropertyFactory.circleStrokeColor(AColor.WHITE),
            PropertyFactory.circleStrokeWidth(3f),
        ))
        s.addLayer(CircleLayer(LYR_SELECTION, SRC_SELECTION).withProperties(
            PropertyFactory.circleRadius(9f),
            PropertyFactory.circleColor(AColor.parseColor("#0B1F33")),
            PropertyFactory.circleStrokeColor(AColor.WHITE),
            PropertyFactory.circleStrokeWidth(3f),
        ))
        s.addLayer(CircleLayer(LYR_LOCATION_HALO, SRC_LOCATION).withProperties(
            PropertyFactory.circleRadius(20f),
            PropertyFactory.circleColor(AColor.parseColor("#33CCFF")),
            PropertyFactory.circleOpacity(0.22f),
            PropertyFactory.circlePitchAlignment(Property.CIRCLE_PITCH_ALIGNMENT_MAP),
        ))
        // Blue heading cursor, rotated with the map so it always points where the user is going.
        s.addLayer(SymbolLayer(LYR_LOCATION, SRC_LOCATION).withProperties(
            PropertyFactory.iconImage(CURSOR),
            PropertyFactory.iconSize(0.6f),
            PropertyFactory.iconRotate(Expression.get("bearing")),
            PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
            PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_MAP),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
        ))
    }

    private fun applyAll() {
        applyAlerts(); applyRoutes(); applyWaypoints(); applySelection(); applyLocation()
    }

    fun setIcons(icons: Map<AlertType, Bitmap>) {
        if (this.icons == icons) return
        this.icons = icons
        style?.let { s -> icons.forEach { (t, b) -> s.addImage(iconName(t), b) } }
    }

    fun setAlerts(list: List<PersonalAlert>, opacity: Float) {
        alerts = list
        alertOpacity = opacity
        applyAlerts()
    }

    fun setRoutes(list: List<Route>, selectedIndex: Int) {
        routes = list
        selected = selectedIndex
        applyRoutes()
    }

    fun setWaypoints(list: List<Pair<GeoPoint, Boolean>>) {
        waypoints = list
        applyWaypoints()
    }

    fun setSelection(p: GeoPoint?) {
        selection = p
        applySelection()
    }

    fun setLocation(f: Fix?) {
        fix = f
        applyLocation()
    }

    private fun applyAlerts() {
        val s = style ?: return
        val features = alerts.map { a ->
            Feature.fromGeometry(Point.fromLngLat(a.point.lon, a.point.lat)).apply {
                addNumberProperty("id", a.id.value)
                addStringProperty("icon", iconName(a.type))
            }
        }
        s.getSourceAs<GeoJsonSource>(SRC_ALERTS)?.setGeoJson(FeatureCollection.fromFeatures(features))
        // Personal alerts are always drawn at a constant reduced opacity (prompt §2.1 D).
        s.getLayer(LYR_ALERTS)?.setProperties(PropertyFactory.iconOpacity(alertOpacity))
        s.getLayer(LYR_CLUSTERS)?.setProperties(
            PropertyFactory.circleOpacity(alertOpacity),
            PropertyFactory.circleStrokeOpacity(alertOpacity),
        )
        s.getLayer(LYR_CLUSTER_COUNT)?.setProperties(PropertyFactory.textOpacity(max(alertOpacity, 0.6f)))
    }

    private fun applyRoutes() {
        val s = style ?: return
        val main = routes.getOrNull(selected)
        val alts = routes.filterIndexed { i, _ -> i != selected }.map { lineFeature(it) }
        s.getSourceAs<GeoJsonSource>(SRC_ALT)?.setGeoJson(FeatureCollection.fromFeatures(alts))
        s.getSourceAs<GeoJsonSource>(SRC_ROUTE)?.setGeoJson(
            FeatureCollection.fromFeatures(listOfNotNull(main?.let { lineFeature(it) })),
        )
    }

    private fun lineFeature(r: Route) =
        Feature.fromGeometry(LineString.fromLngLats(r.shape.map { Point.fromLngLat(it.lon, it.lat) }))

    private fun applyWaypoints() {
        val s = style ?: return
        val f = waypoints.map { (p, dest) ->
            Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat)).apply { addBooleanProperty("dest", dest) }
        }
        s.getSourceAs<GeoJsonSource>(SRC_WAYPOINTS)?.setGeoJson(FeatureCollection.fromFeatures(f))
    }

    private fun applySelection() {
        val s = style ?: return
        val f = listOfNotNull(selection?.let { Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)) })
        s.getSourceAs<GeoJsonSource>(SRC_SELECTION)?.setGeoJson(FeatureCollection.fromFeatures(f))
    }

    private fun applyLocation() {
        val s = style ?: return
        val f = listOfNotNull(fix?.let {
            Feature.fromGeometry(Point.fromLngLat(it.point.lon, it.point.lat)).apply {
                addNumberProperty("bearing", it.bearingDeg ?: 0f)
            }
        })
        s.getSourceAs<GeoJsonSource>(SRC_LOCATION)?.setGeoJson(FeatureCollection.fromFeatures(f))
    }

    fun moveTo(p: GeoPoint, zoom: Double, bearing: Double = 0.0) {
        val pos = CameraPosition.Builder().target(LatLng(p.lat, p.lon)).zoom(zoom).tilt(0.0).bearing(bearing).build()
        lastCamera = pos
        if (style == null) map.moveCamera(CameraUpdateFactory.newCameraPosition(pos))
        else map.animateCamera(CameraUpdateFactory.newCameraPosition(pos), 700)
    }

    /** True while the user pinches; camera following pauses so the gesture is not fought. */
    var scaling = false
        private set

    /** Browsing camera: keeps the user centred, optionally heading-up, keeping the user's zoom. */
    fun followBrowse(f: Fix, headingUp: Boolean, tiltDeg: Double) {
        if (scaling) return
        val pos = CameraPosition.Builder()
            .target(LatLng(f.point.lat, f.point.lon))
            .bearing(if (headingUp) (f.bearingDeg ?: map.cameraPosition.bearing.toFloat()).toDouble() else 0.0)
            .tilt(if (headingUp) tiltDeg else 0.0)
            .build()
        map.animateCamera(CameraUpdateFactory.newCameraPosition(pos), 700)
    }

    /** Applies a newly saved zoom / tilt right away, keeping the current target and bearing. */
    fun applyView(zoom: Double, tiltDeg: Double) {
        val pos = CameraPosition.Builder(map.cameraPosition).zoom(zoom).tilt(tiltDeg).build()
        lastCamera = pos
        map.animateCamera(CameraUpdateFactory.newCameraPosition(pos), 600)
    }

    /**
     * Driving camera: user zoom at low speed, zooming out progressively with speed; flat top-down by
     * default, optional tilt toward the direction of travel; cursor slightly below the centre.
     */
    fun follow(f: Fix, headingUp: Boolean, viewHeightPx: Int, baseZoom: Double, tiltDeg: Double) {
        if (scaling) return
        val pos = CameraPosition.Builder()
            .target(LatLng(f.point.lat, f.point.lon))
            .zoom((baseZoom - speedZoomOffset(f.speedMps)).coerceIn(13.0, 21.0))
            .tilt(if (headingUp) tiltDeg else 0.0)
            .bearing(if (headingUp) (f.bearingDeg ?: map.cameraPosition.bearing.toFloat()).toDouble() else 0.0)
            .padding(0.0, viewHeightPx * (if (tiltDeg > 10) 0.3 else 0.2), 0.0, 0.0)
            .build()
        map.animateCamera(CameraUpdateFactory.newCameraPosition(pos), 900)
    }

    fun fit(points: List<GeoPoint>, padLeft: Int, padTop: Int, padRight: Int, padBottom: Int) {
        if (points.size < 2) return
        val b = LatLngBounds.Builder().includes(points.map { LatLng(it.lat, it.lon) }).build()
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(b, padLeft, padTop, padRight, padBottom), 700)
    }

    private fun handleClick(ll: LatLng): Boolean {
        val px = map.projection.toScreenLocation(ll)
        val hit = map.queryRenderedFeatures(px, LYR_ALERTS).firstOrNull()
        if (hit != null) {
            hit.getNumberProperty("id")?.let { onAlertClick(AlertId(it.toLong())) }
            return true
        }
        val cluster = map.queryRenderedFeatures(px, LYR_CLUSTERS).firstOrNull()
        if (cluster != null) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(ll, map.cameraPosition.zoom + 2), 500)
            return true
        }
        return false
    }

    companion object {
        private const val SRC_ALERTS = "cap-alerts-src"
        private const val SRC_ALT = "cap-alt-src"
        private const val SRC_ROUTE = "cap-route-src"
        private const val SRC_WAYPOINTS = "cap-wp-src"
        private const val SRC_SELECTION = "cap-sel-src"
        private const val SRC_LOCATION = "cap-loc-src"
        private const val LYR_CLUSTERS = "cap-alert-clusters"
        private const val LYR_CLUSTER_COUNT = "cap-alert-count"
        private const val LYR_ALERTS = "cap-alerts"
        private const val LYR_ALT = "cap-route-alt"
        private const val LYR_ROUTE_CASING = "cap-route-casing"
        private const val LYR_ROUTE = "cap-route"
        private const val LYR_WAYPOINTS = "cap-waypoints"
        private const val LYR_SELECTION = "cap-selection"
        private const val LYR_LOCATION_HALO = "cap-location-halo"
        private const val LYR_LOCATION = "cap-location"

        private const val LYR_GREEN = "cap-green"
        private const val CURSOR = "cap-cursor"

        fun iconName(t: AlertType) = "alert-${t.name}"

        fun speedZoomOffset(speedMps: Float): Double {
            val kmh = speedMps * 3.6
            return when {
                kmh > 100 -> 2.0
                kmh > 60 -> 1.3
                kmh > 30 -> 0.6
                else -> 0.0
            }
        }

        /** Arrow-head cursor (brand blue, deep-blue outline for contrast on light maps), pointing up = north before rotation. */
        private fun cursorBitmap(): Bitmap {
            val size = 120
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(bmp)
            val path = android.graphics.Path().apply {
                moveTo(size * 0.5f, size * 0.08f)
                lineTo(size * 0.86f, size * 0.88f)
                lineTo(size * 0.5f, size * 0.68f)
                lineTo(size * 0.14f, size * 0.88f)
                close()
            }
            val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = AColor.parseColor("#33CCFF") }
            val stroke = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = size * 0.08f
                strokeJoin = android.graphics.Paint.Join.ROUND
                color = AColor.parseColor("#0A6CC4")
            }
            c.drawPath(path, stroke)
            c.drawPath(path, fill)
            return bmp
        }
    }
}
