package org.capnav.app.ui.drive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.capnav.app.R
import org.capnav.app.data.settings.Units
import org.capnav.app.navigation.TripState
import org.capnav.app.ui.CapViewModel
import org.capnav.app.ui.common.Format
import org.capnav.app.ui.common.KeyValue
import org.capnav.app.ui.common.PrimaryButton
import org.capnav.app.ui.common.SecondaryButton
import org.capnav.app.ui.common.VSpace

@Composable
fun TripSummaryScreen(vm: CapViewModel, s: TripState.Finished, units: Units) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                stringResource(if (s.reachedDestination) R.string.arrived else R.string.trip_ended),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(s.summary.destinationName, style = MaterialTheme.typography.titleLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                KeyValue(stringResource(R.string.actual_duration), Format.duration(s.summary.actualS))
                KeyValue(stringResource(R.string.estimated_duration), Format.duration(s.summary.estimatedS))
                KeyValue(stringResource(R.string.distance), Format.distance(s.summary.distanceM, units))
            }
            val diff = s.summary.actualS - s.summary.estimatedS
            if (s.reachedDestination && kotlin.math.abs(diff) >= 60) Text(
                stringResource(if (diff > 0) R.string.later_than_estimate else R.string.earlier_than_estimate, Format.duration(kotlin.math.abs(diff))),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            VSpace(8.dp)
            s.summary.waypoints.lastOrNull()?.let { dest ->
                SecondaryButton(stringResource(R.string.save_as_favorite), { vm.saveDestinationFavorite(dest.place) }, Modifier.fillMaxWidth(), Icons.Outlined.Star)
            }
            PrimaryButton(stringResource(R.string.close), { vm.dismissSummary() }, Modifier.fillMaxWidth())
        }
    }
}
