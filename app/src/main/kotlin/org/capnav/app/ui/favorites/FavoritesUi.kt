package org.capnav.app.ui.favorites

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BeachAccess
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.LocalParking
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.capnav.app.R
import org.capnav.app.data.places.Favorite
import org.capnav.app.data.places.FavoriteIcon
import org.capnav.app.data.places.FavoriteKind
import org.capnav.app.data.places.PlacesRepository
import org.capnav.app.ui.CapViewModel
import org.capnav.app.ui.Screen
import org.capnav.app.ui.common.PrimaryButton
import org.capnav.app.ui.common.ScreenHeader
import org.capnav.app.ui.common.SectionTitle
import org.capnav.app.ui.theme.Brand
import org.capnav.app.ui.theme.LocalSemantic

fun FavoriteIcon.vector(): ImageVector = when (this) {
    FavoriteIcon.STAR -> Icons.Outlined.Star
    FavoriteIcon.HOME -> Icons.Outlined.Home
    FavoriteIcon.WORK -> Icons.Outlined.Work
    FavoriteIcon.HEART -> Icons.Outlined.FavoriteBorder
    FavoriteIcon.FAMILY -> Icons.Outlined.FamilyRestroom
    FavoriteIcon.SCHOOL -> Icons.Outlined.School
    FavoriteIcon.SPORT -> Icons.Outlined.FitnessCenter
    FavoriteIcon.SHOP -> Icons.Outlined.ShoppingCart
    FavoriteIcon.RESTAURANT -> Icons.Outlined.Restaurant
    FavoriteIcon.CAFE -> Icons.Outlined.LocalCafe
    FavoriteIcon.HOSPITAL -> Icons.Outlined.LocalHospital
    FavoriteIcon.PARKING -> Icons.Outlined.LocalParking
    FavoriteIcon.FUEL -> Icons.Outlined.LocalGasStation
    FavoriteIcon.TRAIN -> Icons.Outlined.Train
    FavoriteIcon.PARK -> Icons.Outlined.Park
    FavoriteIcon.BEACH -> Icons.Outlined.BeachAccess
}

/** Favourite being created or edited: name, icon and role (home / work / other). */
data class FavoriteDraft(val original: Favorite?, val fav: Favorite)

@Composable
fun FavoritesScreen(vm: CapViewModel) {
    val places by vm.places.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf<Favorite?>(null) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
            ScreenHeader(stringResource(R.string.favorites), onBack = { vm.screen = Screen.MAP })
            LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                item {
                    Text(stringResource(R.string.favorites_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SectionTitle(stringResource(R.string.favorites))
                }
                if (places.favorites.isEmpty()) item {
                    Text(stringResource(R.string.no_favorites), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 24.dp))
                }
                val sorted = places.favorites.sortedBy { it.kind.ordinal }
                items(sorted, key = { "${it.kind}${it.place.point.lat},${it.place.point.lon}" }) { f ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.routeTo(f.place); vm.screen = Screen.MAP }.heightIn(min = 72.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FavoriteBadge(f.icon)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(f.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val role = when (f.kind) {
                                FavoriteKind.HOME -> stringResource(R.string.home) + " · "
                                FavoriteKind.WORK -> stringResource(R.string.work) + " · "
                                FavoriteKind.CUSTOM -> ""
                            }
                            Text(
                                role + listOf(f.place.name, f.place.detail).filter { it.isNotBlank() && it != f.displayName }.joinToString(", "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = { vm.editFavorite(f) }, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Outlined.Edit, stringResource(R.string.edit))
                        }
                        IconButton(onClick = { confirmDelete = f }, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Outlined.Delete, stringResource(R.string.delete), tint = LocalSemantic.current.danger)
                        }
                    }
                    HorizontalDivider()
                }
            }
            PrimaryButton(
                stringResource(R.string.add_favorite), { vm.openSearchForFavorite() },
                Modifier.fillMaxWidth().padding(16.dp), icon = Icons.Outlined.Add,
            )
        }
    }
    confirmDelete?.let { f ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.delete_favorite_title)) },
            text = { Text(f.displayName) },
            confirmButton = {
                TextButton(onClick = { vm.removeFavorite(f); confirmDelete = null }) {
                    Text(stringResource(R.string.delete), color = LocalSemantic.current.danger)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
fun FavoriteBadge(icon: FavoriteIcon, selected: Boolean = false) {
    Surface(
        shape = CircleShape,
        color = if (selected) Brand.purple else MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Brand.purple else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.size(48.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon.vector(), null, tint = if (selected) Brand.white else MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
        }
    }
}

/** Name, icon and role editor used both for new favourites and for edits. */
@Composable
fun FavoriteEditorDialog(draft: FavoriteDraft, onDismiss: () -> Unit, onSave: (FavoriteDraft) -> Unit) {
    var name by remember(draft) { mutableStateOf(draft.fav.displayName) }
    var icon by remember(draft) { mutableStateOf(draft.fav.icon) }
    var kind by remember(draft) { mutableStateOf(draft.fav.kind) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (draft.original == null) R.string.add_favorite else R.string.edit_favorite)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(PlacesRepository.MAX_LABEL) },
                    label = { Text(stringResource(R.string.favorite_name)) },
                    singleLine = true,
                    supportingText = { Text(draft.fav.place.detail.ifEmpty { draft.fav.place.name }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FavoriteKind.entries.forEach { k ->
                        FilterChip(
                            selected = kind == k,
                            onClick = {
                                kind = k
                                if (k == FavoriteKind.HOME) icon = FavoriteIcon.HOME
                                if (k == FavoriteKind.WORK) icon = FavoriteIcon.WORK
                            },
                            label = {
                                Text(stringResource(when (k) {
                                    FavoriteKind.HOME -> R.string.home
                                    FavoriteKind.WORK -> R.string.work
                                    FavoriteKind.CUSTOM -> R.string.other
                                }))
                            },
                        )
                    }
                }
                Text(stringResource(R.string.icon), style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FavoriteIcon.entries.forEach { i ->
                        val desc = i.name.lowercase()
                        Box(Modifier.clickable { icon = i }.semantics { contentDescription = desc; selected = icon == i }) {
                            FavoriteBadge(i, selected = icon == i)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val label = PlacesRepository.sanitizeLabel(name)?.takeIf { it != draft.fav.place.name }
                onSave(draft.copy(fav = draft.fav.copy(kind = kind, icon = icon, label = label)))
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
