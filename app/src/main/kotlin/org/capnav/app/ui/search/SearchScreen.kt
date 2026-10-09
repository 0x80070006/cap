package org.capnav.app.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.capnav.app.R
import org.capnav.app.model.Place
import org.capnav.app.model.PoiCategory
import org.capnav.app.navigation.Geo
import org.capnav.app.ui.CapViewModel
import org.capnav.app.ui.Screen
import org.capnav.app.ui.common.Format
import org.capnav.app.ui.common.ScreenHeader
import org.capnav.app.ui.common.SectionTitle
import org.capnav.app.ui.drive.poiLabel
import org.capnav.app.ui.favorites.vector

@Composable
fun SearchScreen(vm: CapViewModel) {
    val places by vm.places.collectAsStateWithLifecycle()
    val fix by vm.fix.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.statusBarsPadding().navigationBarsPadding().imePadding()) {
            ScreenHeader(
                stringResource(
                    when {
                        vm.pickingOrigin -> R.string.start_point
                        vm.pickingFavorite -> R.string.add_favorite
                        vm.pickingStop -> R.string.add_stop
                        else -> R.string.search
                    },
                ),
                onBack = {
                    vm.pickingStop = false
                    vm.pickingOrigin = false
                    vm.screen = if (vm.pickingFavorite) Screen.FAVORITES else Screen.MAP
                    vm.pickingFavorite = false
                },
            )
            OutlinedTextField(
                value = vm.query,
                onValueChange = { vm.query = it.take(120) },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                trailingIcon = {
                    if (vm.query.isNotEmpty()) IconButton(onClick = { vm.query = "" }) {
                        Icon(Icons.Outlined.Clear, stringResource(R.string.clear))
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.results.firstOrNull()?.let(vm::pick) }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focus),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PoiCategory.entries.forEach { c ->
                    AssistChip(onClick = { vm.searchCategory(c) }, label = { Text(stringResource(poiLabel(c))) }, modifier = Modifier.heightIn(min = 48.dp))
                }
            }
            if (vm.searching) LinearProgressIndicator(Modifier.fillMaxWidth())

            LazyColumn(Modifier.weight(1f)) {
                if (vm.searchError) item {
                    Text(stringResource(R.string.search_error), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                }
                if (vm.results.isNotEmpty()) {
                    items(vm.results, key = { "r${it.point.lat},${it.point.lon},${it.name}" }) { p ->
                        PlaceRow(Icons.Outlined.Place, p, fix?.let { Format.distance(Geo.distanceM(it.point, p.point), settings.units) }) { vm.pick(p) }
                    }
                } else if (vm.query.isBlank()) {
                    if (vm.pickingOrigin) item {
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.pickingOrigin = false; vm.screen = Screen.MAP; vm.setOrigin(null) }
                                .heightIn(min = 64.dp).padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.MyLocation, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(16.dp))
                            Text(stringResource(R.string.my_position), style = MaterialTheme.typography.titleMedium)
                        }
                        HorizontalDivider()
                    }
                    if (places.favorites.isNotEmpty()) {
                        item { SectionTitle(stringResource(R.string.favorites), Modifier.padding(horizontal = 16.dp)) }
                        items(places.favorites, key = { "f${it.kind}${it.place.point.lat},${it.place.point.lon}" }) { f ->
                            PlaceRow(f.icon.vector(), f.place.copy(name = f.displayName), null) { vm.pick(f.place) }
                        }
                    }
                    if (places.recents.isNotEmpty()) {
                        item { SectionTitle(stringResource(R.string.recents), Modifier.padding(horizontal = 16.dp)) }
                        items(places.recents, key = { "h${it.point.lat},${it.point.lon},${it.name}" }) { p ->
                            PlaceRow(Icons.Outlined.History, p, null) { vm.pick(p) }
                        }
                    }
                    item {
                        Text(
                            stringResource(R.string.search_privacy_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                } else if (!vm.searching && !vm.searchError) item {
                    Text(stringResource(R.string.no_results), modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

@Composable
private fun PlaceRow(icon: ImageVector, p: Place, distance: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (p.detail.isNotEmpty()) Text(
                p.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        if (distance != null) Text(distance, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(Modifier.padding(start = 56.dp))
}
