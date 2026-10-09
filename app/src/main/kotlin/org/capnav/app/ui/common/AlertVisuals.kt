package org.capnav.app.ui.common

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CarCrash
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.EditLocationAlt
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.LocalPolice
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Sos
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.Traffic
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import org.capnav.app.R
import org.capnav.app.model.AlertType

/**
 * Each alert type is distinguishable by pictogram shape AND colour (never colour alone).
 * Colours come from the brand's secondary accents, reserved for alert pictograms (prompt §6).
 */
data class AlertVisual(val icon: ImageVector, val color: Color, @StringRes val label: Int)

fun AlertType.visual(): AlertVisual = when (this) {
    AlertType.TRAFFIC_JAM -> AlertVisual(Icons.Outlined.Traffic, Color(0xFFD9480F), R.string.alert_traffic_jam)
    AlertType.POLICE -> AlertVisual(Icons.Outlined.LocalPolice, Color(0xFF0A6CC4), R.string.alert_police)
    AlertType.ACCIDENT -> AlertVisual(Icons.Outlined.CarCrash, Color(0xFFC62828), R.string.alert_accident)
    AlertType.HAZARD -> AlertVisual(Icons.Outlined.Warning, Color(0xFFB7791F), R.string.alert_hazard)
    AlertType.ROAD_CLOSED -> AlertVisual(Icons.Outlined.Block, Color(0xFF8E1010), R.string.alert_road_closed)
    AlertType.LANE_BLOCKED -> AlertVisual(Icons.Outlined.DoNotDisturbOn, Color(0xFFD9480F), R.string.alert_lane_blocked)
    AlertType.MAP_ISSUE -> AlertVisual(Icons.Outlined.EditLocationAlt, Color(0xFF5A6E82), R.string.alert_map_issue)
    AlertType.BAD_WEATHER -> AlertVisual(Icons.Outlined.Thunderstorm, Color(0xFF3D5A80), R.string.alert_bad_weather)
    AlertType.FUEL_PRICE -> AlertVisual(Icons.Outlined.LocalGasStation, Color(0xFF1E7E45), R.string.alert_fuel_price)
    AlertType.ROADSIDE_HELP -> AlertVisual(Icons.Outlined.Sos, Color(0xFFC62828), R.string.alert_roadside_help)
    AlertType.MAP_NOTE -> AlertVisual(Icons.AutoMirrored.Outlined.StickyNote2, Color(0xFF8F5BFF), R.string.alert_map_note)
    AlertType.PLACE -> AlertVisual(Icons.Outlined.Place, Color(0xFF8F5BFF), R.string.alert_place)
    AlertType.ROADWORKS -> AlertVisual(Icons.Outlined.Construction, Color(0xFFB7791F), R.string.alert_roadworks)
    AlertType.SPEED_CAMERA -> AlertVisual(Icons.Outlined.PhotoCamera, Color(0xFF0B1F33), R.string.alert_speed_camera)
}

/** The 12 categories of the "What do you see?" sheet, default order (user-reorderable later). */
val ReportGrid = listOf(
    AlertType.TRAFFIC_JAM, AlertType.POLICE, AlertType.ACCIDENT,
    AlertType.HAZARD, AlertType.ROAD_CLOSED, AlertType.LANE_BLOCKED,
    AlertType.MAP_ISSUE, AlertType.BAD_WEATHER, AlertType.FUEL_PRICE,
    AlertType.ROADSIDE_HELP, AlertType.MAP_NOTE, AlertType.PLACE,
)

val ExtraReportTypes = listOf(AlertType.ROADWORKS, AlertType.SPEED_CAMERA)

@StringRes
fun subtypeLabel(subtype: String): Int = when (subtype) {
    "moderate" -> R.string.sub_moderate
    "heavy" -> R.string.sub_heavy
    "standstill" -> R.string.sub_standstill
    "visible" -> R.string.sub_visible
    "hidden" -> R.string.sub_hidden
    "my_side" -> R.string.sub_my_side
    "other_side" -> R.string.sub_other_side
    "minor" -> R.string.sub_minor
    "major" -> R.string.sub_major
    "object" -> R.string.sub_object
    "pothole" -> R.string.sub_pothole
    "stopped_vehicle" -> R.string.sub_stopped_vehicle
    "animal" -> R.string.sub_animal
    "fog" -> R.string.sub_fog
    "ice" -> R.string.sub_ice
    "slippery" -> R.string.sub_slippery
    "lights_out" -> R.string.sub_lights_out
    "works" -> R.string.sub_works
    "accident" -> R.string.sub_accident
    "event" -> R.string.sub_event
    "left" -> R.string.sub_left
    "center" -> R.string.sub_center
    "right" -> R.string.sub_right
    "address" -> R.string.sub_address
    "missing_road" -> R.string.sub_missing_road
    "wrong_oneway" -> R.string.sub_wrong_oneway
    "wrong_turn" -> R.string.sub_wrong_turn
    "wrong_speed" -> R.string.sub_wrong_speed
    "place_closed" -> R.string.sub_place_closed
    "rain" -> R.string.sub_rain
    "storm" -> R.string.sub_storm
    "hail" -> R.string.sub_hail
    "snow" -> R.string.sub_snow
    "wind" -> R.string.sub_wind
    "flood" -> R.string.sub_flood
    "breakdown" -> R.string.sub_breakdown
    "assistance" -> R.string.sub_assistance
    "fixed" -> R.string.sub_fixed
    "mobile" -> R.string.sub_mobile
    else -> R.string.sub_other
}
