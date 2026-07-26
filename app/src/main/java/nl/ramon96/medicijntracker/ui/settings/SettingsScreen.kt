package nl.ramon96.medicijntracker.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.ramon96.medicijntracker.BuildConfig
import nl.ramon96.medicijntracker.R
import nl.ramon96.medicijntracker.data.prefs.AppFont
import nl.ramon96.medicijntracker.data.prefs.AppSettings
import nl.ramon96.medicijntracker.data.prefs.ThemeMode
import nl.ramon96.medicijntracker.ui.common.openExactAlarmSettings
import nl.ramon96.medicijntracker.ui.common.openNotificationSettings
import nl.ramon96.medicijntracker.ui.common.rememberPermissionStatus
import nl.ramon96.medicijntracker.ui.common.requestIgnoreBatteryOptimisations
import nl.ramon96.medicijntracker.ui.theme.Palettes
import nl.ramon96.medicijntracker.update.UpdateError
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppearanceSection(viewModel)
        HorizontalDivider()
        ReminderSection(viewModel)
        HorizontalDivider()
        ScanningSection(viewModel)
        HorizontalDivider()
        UpdateSection(viewModel)
        HorizontalDivider()
        DeliverySection()
    }
}

// --- Weergave ---------------------------------------------------------------

@Composable
private fun AppearanceSection(viewModel: SettingsViewModel) {
    val theme by viewModel.theme.collectAsStateWithLifecycle()

    SectionTitle(stringResource(R.string.settings_appearance))

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeMode.entries.forEach { mode ->
            FilterChip(
                selected = theme.mode == mode,
                onClick = { viewModel.setThemeMode(mode) },
                label = { Text(themeModeLabel(mode)) },
            )
        }
    }

    SwitchRow(
        label = stringResource(R.string.settings_dynamic_color),
        description = stringResource(R.string.settings_dynamic_color_hint),
        checked = theme.dynamicColor,
        onCheckedChange = viewModel::setDynamicColor,
    )

    if (!theme.dynamicColor) {
        Text(stringResource(R.string.settings_palette), style = MaterialTheme.typography.bodyMedium)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Palettes.all.forEach { palette ->
                PaletteSwatch(
                    color = palette.swatch,
                    label = palette.label,
                    selected = theme.paletteId == palette.id,
                    onClick = { viewModel.setPalette(palette.id) },
                )
            }
        }
    }

    SwitchRow(
        label = stringResource(R.string.settings_high_contrast),
        description = stringResource(R.string.settings_high_contrast_hint),
        checked = theme.highContrast,
        onCheckedChange = viewModel::setHighContrast,
    )

    Text(
        text = stringResource(R.string.settings_text_size, (theme.textScale * 100).roundToInt()),
        style = MaterialTheme.typography.bodyMedium,
    )
    Slider(
        value = theme.textScale,
        onValueChange = { viewModel.setTextScale(it) },
        valueRange = AppSettings.MIN_TEXT_SCALE..AppSettings.MAX_TEXT_SCALE,
    )
    // Live preview: this line is already scaled, so the effect is visible while dragging.
    Card(Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.settings_text_preview),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(12.dp),
        )
    }

    Text(stringResource(R.string.settings_font), style = MaterialTheme.typography.bodyMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppFont.entries.forEach { font ->
            FilterChip(
                selected = theme.font == font,
                onClick = { viewModel.setFont(font) },
                label = { Text(fontLabel(font)) },
            )
        }
    }
}

@Composable
private fun PaletteSwatch(
    color: Color,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = if (selected) 3.dp else 0.dp,
                    color = MaterialTheme.colorScheme.onSurface,
                    shape = CircleShape,
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

// --- Herinneringen ----------------------------------------------------------

// --- Scannen ----------------------------------------------------------------

@Composable
private fun ScanningSection(viewModel: SettingsViewModel) {
    val lookupEnabled by viewModel.barcodeLookup.collectAsStateWithLifecycle()

    SectionTitle(stringResource(R.string.settings_scanning))

    SwitchRow(
        label = stringResource(R.string.settings_barcode_lookup),
        description = stringResource(R.string.settings_barcode_lookup_hint),
        checked = lookupEnabled,
        onCheckedChange = viewModel::setBarcodeLookup,
    )
}

// --- Herinneringen ----------------------------------------------------------

@Composable
private fun ReminderSection(viewModel: SettingsViewModel) {
    val snoozeMinutes by viewModel.defaultSnoozeMinutes.collectAsStateWithLifecycle()
    val throttleDays by viewModel.refillThrottleDays.collectAsStateWithLifecycle()

    SectionTitle(stringResource(R.string.settings_reminders))

    Text(
        text = pluralStringResource(R.plurals.settings_snooze, snoozeMinutes, snoozeMinutes),
        style = MaterialTheme.typography.bodyMedium,
    )
    Slider(
        value = snoozeMinutes.toFloat(),
        onValueChange = { viewModel.setDefaultSnooze(it.roundToInt()) },
        valueRange = 5f..60f,
        steps = 10,
    )
    Text(stringResource(R.string.settings_snooze_hint), style = MaterialTheme.typography.bodySmall)

    Text(
        text = pluralStringResource(R.plurals.settings_refill_throttle, throttleDays, throttleDays),
        style = MaterialTheme.typography.bodyMedium,
    )
    Slider(
        value = throttleDays.toFloat(),
        onValueChange = { viewModel.setRefillThrottle(it.roundToInt()) },
        valueRange = 1f..14f,
        steps = 12,
    )
    Text(
        text = stringResource(R.string.settings_refill_throttle_hint),
        style = MaterialTheme.typography.bodySmall,
    )
}

// --- Updates ----------------------------------------------------------------

@Composable
private fun UpdateSection(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val state by viewModel.update.collectAsStateWithLifecycle()
    val autoCheck by viewModel.autoUpdateCheck.collectAsStateWithLifecycle()

    SectionTitle(stringResource(R.string.settings_updates))
    Text(
        text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
        style = MaterialTheme.typography.bodyMedium,
    )

    SwitchRow(
        label = stringResource(R.string.settings_auto_update),
        description = stringResource(R.string.settings_auto_update_hint),
        checked = autoCheck,
        onCheckedChange = { viewModel.setAutoUpdateCheck(it, context) },
    )

    when (val current = state) {
        is UpdateUiState.Idle -> {
            Button(onClick = viewModel::checkForUpdate) {
                Text(stringResource(R.string.settings_check_updates))
            }
        }

        is UpdateUiState.Checking -> {
            Text(stringResource(R.string.settings_checking))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        is UpdateUiState.UpToDate -> {
            Text(stringResource(R.string.settings_up_to_date))
            TextButton(onClick = viewModel::dismissUpdateMessage) {
                Text(stringResource(R.string.settings_check_again))
            }
        }

        is UpdateUiState.Available -> {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_update_available, current.info.versionName),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (current.info.releaseNotes.isNotBlank()) {
                        Text(current.info.releaseNotes, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = { viewModel.downloadAndInstall(current.info) }) {
                        Text(stringResource(R.string.settings_download_install))
                    }
                }
            }
        }

        is UpdateUiState.Downloading -> {
            Text(stringResource(R.string.settings_downloading))
            if (current.fraction != null) {
                LinearProgressIndicator({ current.fraction }, Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }

        is UpdateUiState.ReadyToInstall -> Text(stringResource(R.string.settings_installing))

        is UpdateUiState.Failed -> {
            Text(
                text = updateErrorMessage(current.error),
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = viewModel::dismissUpdateMessage) {
                Text(stringResource(R.string.settings_check_again))
            }
        }
    }
}

@Composable
private fun updateErrorMessage(error: UpdateError): String = stringResource(
    when (error) {
        UpdateError.NO_NETWORK -> R.string.update_error_network
        UpdateError.RATE_LIMITED -> R.string.update_error_rate_limited
        UpdateError.NO_RELEASE -> R.string.update_error_no_release
        UpdateError.NO_APK -> R.string.update_error_no_apk
        UpdateError.UNKNOWN -> R.string.update_error_unknown
    },
)

// --- Meldingen --------------------------------------------------------------

@Composable
private fun DeliverySection() {
    val context = LocalContext.current
    val status = rememberPermissionStatus()

    SectionTitle(stringResource(R.string.settings_delivery_title))
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusRow(
                label = stringResource(R.string.settings_notifications),
                ok = status.notificationsEnabled,
                onFix = { openNotificationSettings(context) },
            )
            StatusRow(
                label = stringResource(R.string.settings_exact_alarms),
                ok = status.exactAlarmsAllowed,
                onFix = { openExactAlarmSettings(context) },
            )
            StatusRow(
                label = stringResource(R.string.settings_battery),
                ok = status.ignoringBatteryOptimisations,
                onFix = { requestIgnoreBatteryOptimisations(context) },
            )
            Text(
                text = stringResource(R.string.settings_delivery_hint),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

// --- shared bits ------------------------------------------------------------

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label)
            description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean, onFix: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (ok) Icons.Default.Check else Icons.Default.Warning,
            contentDescription = null,
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Text(label, modifier = Modifier.padding(start = 8.dp).weight(1f))
        if (!ok) {
            TextButton(onClick = onFix) { Text(stringResource(R.string.action_fix)) }
        }
    }
}

@Composable
private fun themeModeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.theme_mode_system
        ThemeMode.LIGHT -> R.string.theme_mode_light
        ThemeMode.DARK -> R.string.theme_mode_dark
    },
)

@Composable
private fun fontLabel(font: AppFont): String = stringResource(
    when (font) {
        AppFont.SANS -> R.string.font_sans
        AppFont.SERIF -> R.string.font_serif
        AppFont.MONO -> R.string.font_mono
    },
)
