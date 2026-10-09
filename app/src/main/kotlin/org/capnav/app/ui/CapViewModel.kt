package org.capnav.app.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.capnav.app.AppContainer
import org.capnav.app.R
import org.capnav.app.data.location.LocationProfile
import org.capnav.app.data.osm.OsmNotes
import org.capnav.app.data.places.FavoriteKind
import org.capnav.app.data.settings.AppSettings
import org.capnav.app.model.AlertId
import org.capnav.app.model.AlertType
import org.capnav.app.model.CreateAlertResult
import org.capnav.app.model.GeoPoint
import org.capnav.app.model.NewPersonalAlert
import org.capnav.app.model.PersonalAlert
import org.capnav.app.model.Place
import org.capnav.app.model.PoiCategory
import org.capnav.app.model.PoiWithDetour
import org.capnav.app.model.Route
import org.capnav.app.model.RouteOptions
import org.capnav.app.model.RouteRequest
import org.capnav.app.model.Waypoint
import org.capnav.app.navigation.Fix
import org.capnav.app.navigation.Geo
import org.capnav.app.navigation.NavigationService
import org.capnav.app.navigation.StopReason
import org.capnav.app.navigation.TripEvent
import org.capnav.app.navigation.TripState
import org.capnav.app.navigation.activeTrip
import org.capnav.app.ui.common.subtypeLabel
import org.capnav.app.ui.common.visual
import java.util.Locale
import kotlin.math.max

enum class Screen { MAP, SEARCH, MY_ALERTS, FAVORITES, SETTINGS, PRIVACY, ABOUT }

data class UiMessage(val id: Long, val text: String, val actionLabel: String? = null, val action: (suspend () -> Unit)? = null)

data class CameraCommand(val point: GeoPoint, val zoom: Double, val bearing: Double = 0.0, val id: Long = System.nanoTime())

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class CapViewModel(private val c: AppContainer, private val app: Application) : ViewModel() {
    val settings: StateFlow<AppSettings> = c.settings.settings
    val trip: StateFlow<TripState> = c.engine.state
    val alerts: StateFlow<List<PersonalAlert>> = c.alerts.alerts
    val places = c.places.data
    val networkLog get() = c.networkLog.entries
    val legal get() = c.legal

    var loaded by mutableStateOf(false); private set
    var screen by mutableStateOf(Screen.MAP)

    private val _fix = MutableStateFlow<Fix?>(null)
    val fix: StateFlow<Fix?> = _fix.asStateFlow()
    private var locationJob: Job? = null
    var locationGranted by mutableStateOf(false); private set

    var camera by mutableStateOf<CameraCommand?>(null); private set
    var followUser by mutableStateOf(true)
    /** Map oriented in the direction of travel (true) or north-up (false). */
    var headingUp by mutableStateOf(true)
    var mapBearing by mutableStateOf(0.0)

    // Search
    var query by mutableStateOf("")
    var results by mutableStateOf<List<Place>>(emptyList()); private set
    var searching by mutableStateOf(false); private set
    var searchError by mutableStateOf(false); private set
    /** When true, a picked search result becomes an extra stop instead of a destination. */
    var pickingStop by mutableStateOf(false)
    /** When true, a picked search result opens the favourite editor. */
    var pickingFavorite by mutableStateOf(false)
    var favoriteDraft by mutableStateOf<org.capnav.app.ui.favorites.FavoriteDraft?>(null)

    var selectedPlace by mutableStateOf<Place?>(null)
    var computingRoute by mutableStateOf(false); private set

    // Reporting
    var reportTarget by mutableStateOf<GeoPoint?>(null); private set
    var lastCreated by mutableStateOf<PersonalAlert?>(null); private set
    var duplicate by mutableStateOf<Pair<NewPersonalAlert, PersonalAlert>?>(null); private set
    var roadsideHelp by mutableStateOf<PersonalAlert?>(null)
    var noteEditor by mutableStateOf<PersonalAlert?>(null)
    var showExtraTypes by mutableStateOf(false)

    var detailAlert by mutableStateOf<AlertId?>(null)
    var osmDraft by mutableStateOf<OsmNotes.Draft?>(null); private set

    // Add a stop while driving
    var addStopOpen by mutableStateOf(false); private set
    var addStopCategory by mutableStateOf<PoiCategory?>(null); private set
    var addStopResults by mutableStateOf<List<PoiWithDetour>>(emptyList()); private set
    var addStopLoading by mutableStateOf(false); private set
    var addStopError by mutableStateOf(false); private set

    // Approach reminders
    var reminder by mutableStateOf<PersonalAlert?>(null); private set
    var reminderDistanceM by mutableStateOf(0.0); private set
    private val remindedThisTrip = HashSet<AlertId>()
    private var routeAlertsFor: Pair<Route, List<PersonalAlert>>? = null
    private var routeAlerts: List<Pair<PersonalAlert, Int>> = emptyList()
    private var routeCumulative: DoubleArray = DoubleArray(0)

    var stopCountdown by mutableStateOf<Int?>(null); private set
    private var stopJob: Job? = null

    var message by mutableStateOf<UiMessage?>(null); private set
    private var nextWaypointId = System.currentTimeMillis()

    init {
        viewModelScope.launch {
            c.settings.load()
            c.places.load()
            c.alerts.load()
            c.engine.restore()
            loaded = true
        }
        viewModelScope.launch { settings.collect { c.voice.enabled = it.voiceEnabled } }
        viewModelScope.launch {
            snapshotFlow { query }.debounce(300).distinctUntilChanged().collectLatest { q -> runSearch(q) }
        }
        viewModelScope.launch { c.engine.events.collect(::onTripEvent) }
        viewModelScope.launch {
            var wasActive = false
            trip.collect { s ->
                val active = s.activeTrip != null
                if (active && !wasActive) NavigationService.start(app)
                if (!active) {
                    reminder = null
                    stopJob?.cancel()
                    stopCountdown = null
                }
                if (s is TripState.Navigating && !wasActive) remindedThisTrip.clear()
                wasActive = active
            }
        }
    }

    // ---------- Location ----------

    fun onLocationPermission(granted: Boolean) {
        locationGranted = granted
        if (!granted || locationJob != null) return
        locationJob = viewModelScope.launch {
            trip.map { it.activeTrip != null }.distinctUntilChanged()
                .flatMapLatest { navigating ->
                    runCatching {
                        c.location.locationUpdates(if (navigating) LocationProfile.NAVIGATION else LocationProfile.BROWSING)
                    }.getOrDefault(emptyFlow())
                }
                .collect { raw ->
                    val prev = _fix.value
                    // Many fixes (low speed, emulators, some chipsets) carry no bearing: derive it from movement.
                    val f = if (raw.bearingDeg == null && prev != null && Geo.distanceM(prev.point, raw.point) > 3.0) {
                        raw.copy(bearingDeg = Geo.bearingDeg(prev.point, raw.point).toFloat())
                    } else if (raw.bearingDeg == null) raw.copy(bearingDeg = prev?.bearingDeg) else raw
                    val first = prev == null
                    _fix.value = f
                    if (first) camera = CameraCommand(f.point, 17.0)
                    c.engine.onLocation(f)
                    checkReminders(f)
                }
        }
    }

    fun recenter() {
        followUser = true
        _fix.value?.let { camera = CameraCommand(it.point, if (trip.value.activeTrip != null) 18.3 else 17.0, currentBearing(it)) }
    }

    /** Recalibrate: re-centre and turn the map so the direction of travel points up. */
    fun reorient() {
        headingUp = true
        recenter()
    }

    fun northUp() {
        headingUp = false
        recenter()
    }

    private fun currentBearing(f: Fix): Double = if (headingUp) (f.bearingDeg ?: 0f).toDouble() else 0.0

    fun showOnMap(point: GeoPoint) {
        followUser = false
        camera = CameraCommand(point, 16.5)
        screen = Screen.MAP
    }

    // ---------- Search & routing ----------

    private suspend fun runSearch(q: String) {
        if (q.isBlank()) {
            results = emptyList(); searchError = false; return
        }
        searching = true
        val r = c.geocoding.search(q, _fix.value?.point, Locale.getDefault().toLanguageTag())
        searching = false
        r.onSuccess { results = it; searchError = false }.onFailure { searchError = true }
    }

    fun searchCategory(category: PoiCategory) {
        val near = _fix.value?.point ?: return toast(R.string.no_position)
        viewModelScope.launch {
            searching = true
            val r = c.geocoding.category(category, near, 15)
            searching = false
            r.onSuccess { list ->
                results = list.sortedBy { Geo.distanceM(near, it.point) }; searchError = false
            }.onFailure { searchError = true }
        }
    }

    fun openSearch(forStop: Boolean = false) {
        pickingStop = forStop
        pickingFavorite = false
        query = ""
        results = emptyList()
        screen = Screen.SEARCH
    }

    fun pick(place: Place) {
        screen = Screen.MAP
        if (pickingFavorite) {
            pickingFavorite = false
            screen = Screen.FAVORITES
            openFavoriteEditor(place)
        } else if (pickingStop) {
            pickingStop = false
            addStop(place)
        } else {
            selectedPlace = place
            followUser = false
            camera = CameraCommand(place.point, 16.0)
        }
    }

    fun onMapLongPress(point: GeoPoint) {
        selectedPlace = Place(
            app.getString(R.string.dropped_pin),
            String.format(Locale.ROOT, "%.5f, %.5f", point.lat, point.lon),
            point,
        )
    }

    fun routeTo(place: Place) {
        val origin = _fix.value?.point ?: return toast(R.string.no_position)
        val wp = Waypoint(nextWaypointId++, place)
        val options = settings.value.routeOptions
        viewModelScope.launch {
            computingRoute = true
            val r = c.routing.computeRoutes(RouteRequest(origin, listOf(wp), options, Locale.getDefault().toLanguageTag()))
            computingRoute = false
            r.onSuccess { routes ->
                if (routes.isEmpty()) return@onSuccess toast(R.string.route_error)
                c.engine.preview(routes, origin, listOf(wp), options)
                c.places.addRecent(place)
                selectedPlace = null
            }.onFailure { toast(R.string.route_error) }
        }
    }

    fun updateRouteOptions(options: RouteOptions) {
        val s = trip.value as? TripState.Previewing ?: return
        viewModelScope.launch {
            c.settings.update { it.copy(routeOptions = options) }
            computingRoute = true
            val r = c.routing.computeRoutes(RouteRequest(s.origin, s.waypoints, options, Locale.getDefault().toLanguageTag()))
            computingRoute = false
            r.onSuccess { routes -> if (routes.isNotEmpty()) c.engine.preview(routes, s.origin, s.waypoints, options) }
                .onFailure { toast(R.string.route_error) }
        }
    }

    fun selectRoute(i: Int) = viewModelScope.launch { c.engine.select(i) }
    fun cancelPreview() = viewModelScope.launch { c.engine.cancelPreview() }
    fun startTrip() = viewModelScope.launch {
        followUser = true
        c.engine.start()
        recenter()
    }

    fun removeWaypoint(id: Long) = viewModelScope.launch { c.engine.removeWaypoint(id) }
    fun moveWaypoint(id: Long, delta: Int) = viewModelScope.launch {
        val list = when (val s = trip.value) {
            is TripState.Previewing -> s.waypoints
            else -> s.activeTrip?.remainingWaypoints ?: return@launch
        }
        val i = list.indexOfFirst { it.id == id }
        val j = i + delta
        // The destination always stays last.
        if (i < 0 || j < 0 || j >= list.size - 1 || i == list.size - 1) return@launch
        val order = list.map { it.id }.toMutableList().apply { add(j, removeAt(i)) }
        c.engine.reorderWaypoints(order)
    }

    fun skipWaypoint() = viewModelScope.launch { c.engine.skipWaypoint() }

    // ---------- Driving ----------

    fun pause() = viewModelScope.launch { c.engine.pause(); c.voice.stop() }
    fun resume() = viewModelScope.launch { c.engine.resume() }

    fun requestStop() {
        stopJob?.cancel()
        stopJob = viewModelScope.launch {
            for (i in STOP_UNDO_SECONDS downTo 1) {
                stopCountdown = i
                delay(1_000)
            }
            stopCountdown = null
            c.voice.stop()
            c.engine.stop(StopReason.USER)
        }
    }

    fun cancelStop() {
        stopJob?.cancel()
        stopCountdown = null
    }

    fun dismissSummary() = viewModelScope.launch { c.engine.dismissSummary() }

    fun saveDestinationFavorite(place: Place) = viewModelScope.launch {
        c.places.setFavorite(FavoriteKind.CUSTOM, place)
        toast(R.string.saved_favorite)
    }

    fun setFavorite(kind: FavoriteKind, place: Place) = viewModelScope.launch {
        c.places.setFavorite(kind, place)
        toast(R.string.saved_favorite)
    }

    fun removeFavorite(f: org.capnav.app.data.places.Favorite) = viewModelScope.launch { c.places.removeFavorite(f) }

    fun openSearchForFavorite() {
        openSearch()
        pickingFavorite = true
    }

    fun openFavoriteEditor(place: Place, kind: FavoriteKind = FavoriteKind.CUSTOM) {
        favoriteDraft = org.capnav.app.ui.favorites.FavoriteDraft(null, org.capnav.app.data.places.Favorite(kind, place))
    }

    fun editFavorite(f: org.capnav.app.data.places.Favorite) {
        favoriteDraft = org.capnav.app.ui.favorites.FavoriteDraft(f, f)
    }

    fun saveFavoriteDraft(d: org.capnav.app.ui.favorites.FavoriteDraft) {
        favoriteDraft = null
        viewModelScope.launch {
            c.places.saveFavorite(d.original, d.fav)
            toast(R.string.saved_favorite)
        }
    }

    fun openAddStop() {
        addStopOpen = true
        addStopCategory = null
        addStopResults = emptyList()
        addStopError = false
    }

    fun closeAddStop() {
        addStopOpen = false
    }

    fun searchAlongRoute(category: PoiCategory) {
        val t = trip.value.activeTrip ?: return
        val pos = _fix.value?.point ?: t.progress.matched
        addStopCategory = category
        viewModelScope.launch {
            addStopLoading = true
            addStopError = false
            val remainingShape = t.route.shape.drop(t.progress.segmentIndex)
            val req = RouteRequest(pos, t.remainingWaypoints, t.options, Locale.getDefault().toLanguageTag())
            val r = c.routing.alongRoutePois(pos, remainingShape, category, req, maxDetourS = 20 * 60)
            addStopLoading = false
            r.onSuccess { addStopResults = it }.onFailure { addStopError = true }
        }
    }

    fun addStop(place: Place) {
        addStopOpen = false
        viewModelScope.launch {
            val nextIndex = if (trip.value is TripState.Previewing) null else 0
            c.engine.addWaypoint(Waypoint(nextWaypointId++, place), nextIndex)
            message(app.getString(R.string.stop_added, place.name))
        }
    }

    private fun onTripEvent(e: TripEvent) {
        when (e) {
            is TripEvent.Announce -> c.voice.speak(e.text)
            is TripEvent.WaypointReached -> {
                message(app.getString(R.string.waypoint_reached, e.waypoint.place.name))
                c.voice.speak(app.getString(R.string.waypoint_reached, e.waypoint.place.name))
            }
            TripEvent.Rerouted -> Unit
            is TripEvent.RerouteFailed -> toast(R.string.reroute_failed)
            TripEvent.Arrived -> c.voice.speak(app.getString(R.string.voice_arrived))
        }
    }

    // ---------- Personal alerts ----------

    fun openReport(at: GeoPoint? = null) {
        val target = at ?: _fix.value?.point ?: return toast(R.string.no_position)
        reportTarget = target
        lastCreated = null
        showExtraTypes = false
    }

    fun closeReport() {
        reportTarget = null
        lastCreated = null
    }

    fun isTypeAllowed(type: AlertType) = c.legal.isAllowed(type, settings.value.country)

    fun report(type: AlertType, force: Boolean = false) {
        val target = reportTarget ?: duplicate?.first?.point ?: return
        if (!isTypeAllowed(type)) return toast(R.string.not_allowed_country)
        val new = NewPersonalAlert(type, target)
        viewModelScope.launch {
            when (val r = c.alerts.create(new, force)) {
                is CreateAlertResult.Created -> onCreated(r.alert)
                is CreateAlertResult.Duplicate -> duplicate = new to r.existing
            }
        }
    }

    private fun onCreated(alert: PersonalAlert) {
        duplicate = null
        lastCreated = alert
        // Reporting a spot you are driving past must not trigger its own approach reminder.
        remindedThisTrip += alert.id
        when (alert.type) {
            AlertType.ROADSIDE_HELP -> {
                reportTarget = null
                roadsideHelp = alert
            }
            AlertType.MAP_NOTE, AlertType.PLACE -> {
                reportTarget = null
                noteEditor = alert
            }
            else -> Unit
        }
    }

    fun mergeDuplicate() {
        val (_, existing) = duplicate ?: return
        duplicate = null
        remindedThisTrip += existing.id
        viewModelScope.launch {
            c.alerts.merge(existing.id)
            message(app.getString(R.string.alert_merged))
            reportTarget = null
        }
    }

    fun createDespiteDuplicate() {
        val (new, _) = duplicate ?: return
        if (reportTarget == null) reportTarget = new.point
        report(new.type, force = true)
    }

    fun dismissDuplicate() {
        duplicate = null
    }

    fun undoLastCreated() {
        val a = lastCreated ?: return
        lastCreated = null
        reportTarget = null
        viewModelScope.launch { c.alerts.delete(a.id) }
    }

    fun setSubtype(id: AlertId, subtype: String?) = viewModelScope.launch {
        c.alerts.setSubtype(id, subtype)
        lastCreated = lastCreated?.takeIf { it.id == id }?.copy(subtype = subtype)
    }

    fun setNote(id: AlertId, note: String?) = viewModelScope.launch { c.alerts.setNote(id, note) }

    fun deleteAlerts(ids: Set<AlertId>) {
        val removed = alerts.value.filter { it.id in ids }
        if (removed.isEmpty()) return
        if (detailAlert in ids) detailAlert = null
        if (reminder?.id in ids) reminder = null
        viewModelScope.launch {
            c.alerts.deleteMany(ids)
            val text = app.resources.getQuantityString(R.plurals.alerts_deleted, removed.size, removed.size)
            message(text, app.getString(R.string.undo)) { c.alerts.restore(removed) }
        }
    }

    fun deleteAllAlerts(type: AlertType?) {
        val removed = alerts.value.filter { type == null || it.type == type }
        if (removed.isEmpty()) return
        viewModelScope.launch {
            c.alerts.deleteAll(type)
            val text = app.resources.getQuantityString(R.plurals.alerts_deleted, removed.size, removed.size)
            message(text, app.getString(R.string.undo)) { c.alerts.restore(removed) }
        }
    }

    fun dismissReminder() {
        reminder = null
    }

    private fun checkReminders(f: Fix) {
        val s = settings.value
        val nav = trip.value as? TripState.Navigating ?: return
        if (!s.approachReminders) return
        val route = nav.trip.route
        val all = alerts.value
        if (routeAlertsFor?.first !== route || routeAlertsFor?.second !== all) {
            routeAlerts = c.alerts.alongRoute(all, route.shape, 0)
            routeCumulative = Geo.cumulative(route.shape)
            routeAlertsFor = route to all
        }
        val seg = nav.trip.progress.segmentIndex
        reminder?.let { r ->
            val entry = routeAlerts.firstOrNull { it.first.id == r.id }
            if (entry == null || entry.second < seg) reminder = null
        }
        if (routeAlerts.isEmpty() || routeCumulative.isEmpty()) return
        val here = routeCumulative[seg.coerceIn(0, routeCumulative.lastIndex)]
        val horizon = max(REMINDER_MIN_M, (f.speedMps * REMINDER_SECONDS).toDouble())
        for ((alert, idx) in routeAlerts) {
            if (idx < seg || alert.id in remindedThisTrip) continue
            val ahead = routeCumulative[idx.coerceIn(0, routeCumulative.lastIndex)] - here
            if (ahead > horizon) break
            if (alert.type in s.reminderDisabledTypes || alert.type in s.hiddenAlertTypes || !isTypeAllowed(alert.type)) continue
            remindedThisTrip += alert.id
            reminder = alert
            reminderDistanceM = ahead
            val label = app.getString(alert.type.visual().label)
            val sub = alert.subtype?.let { app.getString(subtypeLabel(it)) }
            c.voice.speak(app.getString(R.string.voice_reminder, sub ?: label))
            viewModelScope.launch {
                delay(REMINDER_VISIBLE_MS)
                if (reminder?.id == alert.id) reminder = null
            }
            break
        }
    }

    // ---------- OpenStreetMap note (manual, previewed) ----------

    fun prepareOsmNote(alert: PersonalAlert) {
        val label = app.getString(alert.type.visual().label)
        val sub = alert.subtype?.let { " — " + app.getString(subtypeLabel(it)) }.orEmpty()
        val text = buildString {
            append(label).append(sub)
            alert.note?.let { append("\n").append(it) }
            append("\n#CapNav")
        }
        osmDraft = c.osmNotes.preview(alert.point, text)
    }

    fun cancelOsmNote() {
        osmDraft = null
    }

    fun sendOsmNote() {
        val d = osmDraft ?: return
        osmDraft = null
        viewModelScope.launch {
            c.osmNotes.send(d).onSuccess { toast(R.string.osm_sent) }.onFailure { toast(R.string.osm_failed) }
        }
    }

    // ---------- Export / import ----------

    fun exportAlerts(uri: Uri, password: CharArray) = viewModelScope.launch {
        val r = c.alerts.exportEncrypted(password)
        password.fill('\u0000')
        r.mapCatching { bytes ->
            withContext(Dispatchers.IO) {
                app.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) }
            }
        }.onSuccess { toast(R.string.export_done) }.onFailure { toast(R.string.export_failed) }
    }

    fun importAlerts(uri: Uri, password: CharArray) = viewModelScope.launch {
        val bytes = runCatching {
            withContext(Dispatchers.IO) {
                app.contentResolver.openInputStream(uri)!!.use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val chunk = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(chunk)
                        if (n < 0) break
                        out.write(chunk, 0, n)
                        require(out.size() <= MAX_IMPORT) { "file too large" }
                    }
                    out.toByteArray()
                }
            }
        }.getOrNull()
        if (bytes == null) {
            password.fill('\u0000')
            return@launch toast(R.string.import_failed)
        }
        val r = c.alerts.importEncrypted(bytes, password)
        password.fill('\u0000')
        r.onSuccess { message(app.resources.getQuantityString(R.plurals.import_done, it, it)) }
            .onFailure { toast(R.string.import_failed) }
    }

    // ---------- Settings & privacy ----------

    fun updateSettings(f: (AppSettings) -> AppSettings) = viewModelScope.launch { c.settings.update(f) }

    fun clearNetworkLog() = c.networkLog.clear()

    fun clearRecents() = viewModelScope.launch { c.places.clearRecents() }

    fun wipeEverything() = viewModelScope.launch {
        c.wipeEverything()
        screen = Screen.MAP
        toast(R.string.everything_erased)
    }

    fun transitions() = c.engine.transitions

    // ---------- Messages ----------

    private fun toast(res: Int) = message(app.getString(res))

    fun message(text: String, actionLabel: String? = null, action: (suspend () -> Unit)? = null) {
        message = UiMessage(System.nanoTime(), text, actionLabel, action)
    }

    fun runMessageAction(m: UiMessage) {
        message = null
        m.action?.let { viewModelScope.launch { it() } }
    }

    fun dismissMessage(m: UiMessage) {
        if (message?.id == m.id) message = null
    }

    override fun onCleared() {
        c.networkLog.flush()
    }

    companion object {
        const val STOP_UNDO_SECONDS = 4
        const val REMINDER_MIN_M = 150.0
        const val REMINDER_SECONDS = 12f
        const val REMINDER_VISIBLE_MS = 12_000L
        const val MAX_IMPORT = 8 shl 20
    }
}
