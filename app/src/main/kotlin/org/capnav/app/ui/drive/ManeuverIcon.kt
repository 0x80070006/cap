package org.capnav.app.ui.drive

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsBoat
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Merge
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.RampRight
import androidx.compose.material.icons.outlined.RoundaboutRight
import androidx.compose.material.icons.outlined.Straight
import androidx.compose.material.icons.outlined.TurnLeft
import androidx.compose.material.icons.outlined.TurnRight
import androidx.compose.material.icons.outlined.TurnSharpLeft
import androidx.compose.material.icons.outlined.TurnSharpRight
import androidx.compose.material.icons.outlined.TurnSlightLeft
import androidx.compose.material.icons.outlined.TurnSlightRight
import androidx.compose.material.icons.outlined.UTurnLeft
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.capnav.app.model.ManeuverKind

@Composable
fun ManeuverIcon(kind: ManeuverKind, modifier: Modifier, tint: Color) {
    val icon = when (kind) {
        ManeuverKind.DEPART -> Icons.Outlined.Navigation
        ManeuverKind.STRAIGHT -> Icons.Outlined.Straight
        ManeuverKind.SLIGHT_RIGHT -> Icons.Outlined.TurnSlightRight
        ManeuverKind.RIGHT -> Icons.Outlined.TurnRight
        ManeuverKind.SHARP_RIGHT -> Icons.Outlined.TurnSharpRight
        ManeuverKind.UTURN -> Icons.Outlined.UTurnLeft
        ManeuverKind.SHARP_LEFT -> Icons.Outlined.TurnSharpLeft
        ManeuverKind.LEFT -> Icons.Outlined.TurnLeft
        ManeuverKind.SLIGHT_LEFT -> Icons.Outlined.TurnSlightLeft
        ManeuverKind.RAMP -> Icons.Outlined.RampRight
        ManeuverKind.MERGE -> Icons.Outlined.Merge
        ManeuverKind.ROUNDABOUT -> Icons.Outlined.RoundaboutRight
        ManeuverKind.FERRY -> Icons.Outlined.DirectionsBoat
        ManeuverKind.WAYPOINT -> Icons.Outlined.Place
        ManeuverKind.ARRIVE -> Icons.Outlined.Flag
    }
    Icon(icon, null, modifier = modifier, tint = tint)
}
