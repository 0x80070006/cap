package org.capnav.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.capnav.app.navigation.TripState
import org.capnav.app.ui.alerts.MyAlertsScreen
import org.capnav.app.ui.map.MainMapScreen
import org.capnav.app.ui.onboarding.OnboardingScreen
import org.capnav.app.ui.search.SearchScreen
import org.capnav.app.ui.settings.AboutScreen
import org.capnav.app.ui.settings.PrivacyScreen
import org.capnav.app.ui.settings.SettingsScreen
import org.capnav.app.ui.theme.CapTheme
import org.capnav.app.ui.theme.isDark

@Composable
fun CapRoot(vm: CapViewModel, onRequestPermissions: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val dark = isDark(settings.theme)
    CapTheme(dark = dark, highContrast = settings.highContrast, accessibleTraffic = settings.accessibleTrafficPalette) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when {
                !vm.loaded -> Unit
                !settings.onboardingDone -> OnboardingScreen(vm, onRequestPermissions)
                else -> {
                    LaunchedEffect(Unit) { onRequestPermissions() }
                    MainMapScreen(vm, dark, onRequestPermissions)
                    when (vm.screen) {
                        Screen.SEARCH -> SearchScreen(vm)
                        Screen.MY_ALERTS -> MyAlertsScreen(vm)
                        Screen.FAVORITES -> org.capnav.app.ui.favorites.FavoritesScreen(vm)
                        Screen.SETTINGS -> SettingsScreen(vm)
                        Screen.PRIVACY -> PrivacyScreen(vm)
                        Screen.ABOUT -> AboutScreen(vm)
                        Screen.MAP -> Unit
                    }
                    vm.favoriteDraft?.let { d ->
                        org.capnav.app.ui.favorites.FavoriteEditorDialog(d, onDismiss = { vm.favoriteDraft = null }, onSave = vm::saveFavoriteDraft)
                    }
                    val trip by vm.trip.collectAsStateWithLifecycle()
                    BackHandler(enabled = vm.screen != Screen.MAP) {
                        vm.screen = when (vm.screen) {
                            Screen.PRIVACY, Screen.ABOUT -> Screen.SETTINGS
                            Screen.SEARCH -> {
                                vm.pickingOrigin = false
                                if (vm.pickingFavorite) Screen.FAVORITES.also { vm.pickingFavorite = false } else Screen.MAP
                            }
                            else -> Screen.MAP
                        }
                    }
                    BackHandler(enabled = vm.screen == Screen.MAP && (vm.selectedPlace != null || trip is TripState.Previewing)) {
                        if (vm.selectedPlace != null) vm.selectedPlace = null else vm.cancelPreview()
                    }
                }
            }
            MessageHost(vm, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp))
        }
    }
}

@Composable
private fun MessageHost(vm: CapViewModel, modifier: Modifier) {
    val host = remember { SnackbarHostState() }
    val msg = vm.message
    LaunchedEffect(msg?.id) {
        val m = msg ?: return@LaunchedEffect
        // Undo stays available exactly 5 s (prompt §2.1 D), plain messages 3 s.
        val timer = launch {
            delay(if (m.actionLabel != null) 5_000 else 3_000)
            host.currentSnackbarData?.dismiss()
        }
        val res = host.showSnackbar(message = m.text, actionLabel = m.actionLabel, duration = SnackbarDuration.Indefinite)
        timer.cancel()
        if (res == SnackbarResult.ActionPerformed) vm.runMessageAction(m) else vm.dismissMessage(m)
    }
    SnackbarHost(host, modifier)
}
