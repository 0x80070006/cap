package org.capnav.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.capnav.app.R
import org.capnav.app.data.settings.AppSettings
import org.capnav.app.data.settings.SettingsRepository
import org.capnav.app.data.settings.ThemeMode
import org.capnav.app.data.settings.Units
import org.capnav.app.model.AlertType
import org.capnav.app.ui.CapViewModel
import org.capnav.app.ui.Screen
import org.capnav.app.ui.common.ScreenHeader
import org.capnav.app.ui.common.SectionTitle
import org.capnav.app.ui.common.visual
import org.capnav.app.ui.routepreview.OptionsEditor

@Composable
fun SettingsScreen(vm: CapViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
            ScreenHeader(stringResource(R.string.settings), onBack = { vm.screen = Screen.MAP })
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                LanguageSwitch()
                NavRow(stringResource(R.string.privacy_dashboard)) { vm.screen = Screen.PRIVACY }
                NavRow(stringResource(R.string.my_alerts)) { vm.screen = Screen.MY_ALERTS }
                NavRow(stringResource(R.string.favorites)) { vm.screen = Screen.FAVORITES }

                SectionTitle(stringResource(R.string.display))
                Chips {
                    ThemeMode.entries.forEach { m ->
                        FilterChip(s.theme == m, { vm.updateSettings { it.copy(theme = m) } }, label = {
                            Text(stringResource(when (m) { ThemeMode.AUTO -> R.string.theme_auto; ThemeMode.LIGHT -> R.string.theme_light; ThemeMode.DARK -> R.string.theme_dark }))
                        })
                    }
                }
                Toggle(stringResource(R.string.high_contrast), s.highContrast) { c -> vm.updateSettings { it.copy(highContrast = c) } }
                Toggle(stringResource(R.string.accessible_traffic), s.accessibleTrafficPalette) { c -> vm.updateSettings { it.copy(accessibleTrafficPalette = c) } }
                Toggle(stringResource(R.string.report_button_left), s.reportButtonLeft) { c -> vm.updateSettings { it.copy(reportButtonLeft = c) } }
                Chips {
                    Units.entries.forEach { u ->
                        FilterChip(s.units == u, { vm.updateSettings { it.copy(units = u) } }, label = {
                            Text(stringResource(if (u == Units.METRIC) R.string.units_metric else R.string.units_imperial))
                        })
                    }
                }

                SectionTitle(stringResource(R.string.map_view))
                SliderSetting(
                    label = stringResource(R.string.nav_zoom, "%.1f".format(s.navZoom)),
                    value = s.navZoom,
                    range = AppSettings.MIN_NAV_ZOOM..AppSettings.MAX_NAV_ZOOM,
                    isDefault = s.navZoom == AppSettings.DEFAULT_NAV_ZOOM,
                    onChange = { v -> vm.updateSettings { it.copy(navZoom = v) } },
                    onReset = { vm.updateSettings { it.copy(navZoom = AppSettings.DEFAULT_NAV_ZOOM) } },
                )
                SliderSetting(
                    label = if (s.navTilt < 1f) stringResource(R.string.nav_tilt_flat) else stringResource(R.string.nav_tilt, s.navTilt.toInt()),
                    value = s.navTilt,
                    range = 0f..AppSettings.MAX_NAV_TILT,
                    isDefault = s.navTilt == AppSettings.DEFAULT_NAV_TILT,
                    onChange = { v -> vm.updateSettings { it.copy(navTilt = v) } },
                    onReset = { vm.updateSettings { it.copy(navTilt = AppSettings.DEFAULT_NAV_TILT) } },
                )
                Text(stringResource(R.string.map_view_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                SectionTitle(stringResource(R.string.voice))
                Toggle(stringResource(R.string.voice_guidance), s.voiceEnabled) { c -> vm.updateSettings { it.copy(voiceEnabled = c) } }
                Text(stringResource(R.string.voice_local_only), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                SectionTitle(stringResource(R.string.route_options))
                OptionsEditor(s.routeOptions) { o -> vm.updateSettings { it.copy(routeOptions = o) } }

                SectionTitle(stringResource(R.string.alerts))
                Text(stringResource(R.string.hidden_on_map), style = MaterialTheme.typography.labelMedium)
                TypeChips(s.hiddenAlertTypes) { t -> vm.updateSettings { it.copy(hiddenAlertTypes = it.hiddenAlertTypes.toggle(t)) } }
                Text(stringResource(R.string.no_reminder_for), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                TypeChips(s.reminderDisabledTypes) { t -> vm.updateSettings { it.copy(reminderDisabledTypes = it.reminderDisabledTypes.toggle(t)) } }

                SectionTitle(stringResource(R.string.country_rules))
                Text(stringResource(R.string.country_rules_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Chips {
                    vm.legal.countries.forEach { c ->
                        FilterChip(s.country == c, { vm.updateSettings { it.copy(country = c) } }, label = { Text(c) })
                    }
                }

                SectionTitle(stringResource(R.string.servers))
                Text(stringResource(R.string.servers_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ServerField(stringResource(R.string.server_routing), s.routingUrl) { u -> vm.updateSettings { it.copy(routingUrl = u) } }
                ServerField(stringResource(R.string.server_geocoder), s.geocoderUrl) { u -> vm.updateSettings { it.copy(geocoderUrl = u) } }
                ServerField(stringResource(R.string.server_style_day), s.styleDayUrl) { u -> vm.updateSettings { it.copy(styleDayUrl = u) } }
                ServerField(stringResource(R.string.server_style_night), s.styleNightUrl) { u -> vm.updateSettings { it.copy(styleNightUrl = u) } }
                TextButton(onClick = {
                    val d = AppSettings()
                    vm.updateSettings { it.copy(routingUrl = d.routingUrl, geocoderUrl = d.geocoderUrl, styleDayUrl = d.styleDayUrl, styleNightUrl = d.styleNightUrl) }
                }) { Text(stringResource(R.string.restore_defaults)) }

                SectionTitle(stringResource(R.string.about))
                NavRow(stringResource(R.string.about_licenses)) { vm.screen = Screen.ABOUT }
                Spacer(Modifier.size(24.dp))
            }
        }
    }
}

/** Français / English, at the very top of the settings. Labels stay in their own language. */
@Composable
private fun LanguageSwitch() {
    val context = LocalContext.current
    val current = remember { AppLanguage.effective(context) }
    SectionTitle(stringResource(R.string.language))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        listOf("fr" to "Français", "en" to "English").forEach { (tag, label) ->
            FilterChip(
                selected = current == tag,
                onClick = { if (current != tag) (context as? android.app.Activity)?.let { AppLanguage.set(it, tag) } },
                label = { Text(label, style = MaterialTheme.typography.titleSmall) },
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            )
        }
    }
}

@Composable
private fun SliderSetting(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    isDefault: Boolean,
    onChange: (Float) -> Unit,
    onReset: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = onReset, enabled = !isDefault) { Text(stringResource(R.string.reset_default)) }
    }
    Slider(value = value, onValueChange = onChange, valueRange = range)
}

private fun Set<AlertType>.toggle(t: AlertType) = if (t in this) this - t else this + t

@Composable
private fun Chips(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) { content() }
}

@Composable
private fun TypeChips(selected: Set<AlertType>, onToggle: (AlertType) -> Unit) {
    Chips {
        AlertType.entries.forEach { t ->
            FilterChip(t in selected, { onToggle(t) }, label = { Text(stringResource(t.visual().label)) })
        }
    }
}

@Composable
fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun NavRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
    }
}

@Composable
private fun ServerField(label: String, value: String, onSave: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    val valid = SettingsRepository.isValidServerUrl(text)
    OutlinedTextField(
        value = text,
        onValueChange = { text = it.take(200) },
        label = { Text(label) },
        singleLine = true,
        isError = !valid,
        supportingText = { if (!valid) Text(stringResource(R.string.https_required)) },
        trailingIcon = {
            if (valid && text != value) TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.save)) }
        },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}
