package nl.ramon96.medicijntracker.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.ramon96.medicijntracker.BuildConfig
import nl.ramon96.medicijntracker.R
import nl.ramon96.medicijntracker.ui.common.openExactAlarmSettings
import nl.ramon96.medicijntracker.ui.common.openNotificationSettings
import nl.ramon96.medicijntracker.ui.common.rememberPermissionStatus
import nl.ramon96.medicijntracker.ui.common.requestIgnoreBatteryOptimisations
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val status = rememberPermissionStatus()
    val snoozeMinutes by viewModel.defaultSnoozeMinutes.collectAsStateWithLifecycle()
    val throttleDays by viewModel.refillThrottleDays.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_delivery_title),
            style = MaterialTheme.typography.titleMedium,
        )
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

        HorizontalDivider()

        Text(
            text = pluralStringResource(R.plurals.settings_snooze, snoozeMinutes, snoozeMinutes),
            style = MaterialTheme.typography.titleMedium,
        )
        Slider(
            value = snoozeMinutes.toFloat(),
            onValueChange = { viewModel.setDefaultSnooze(it.roundToInt()) },
            valueRange = 5f..60f,
            steps = 10,
        )
        Text(
            text = stringResource(R.string.settings_snooze_hint),
            style = MaterialTheme.typography.bodySmall,
        )

        HorizontalDivider()

        Text(
            text = pluralStringResource(R.plurals.settings_refill_throttle, throttleDays, throttleDays),
            style = MaterialTheme.typography.titleMedium,
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

        HorizontalDivider()

        Text(
            text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall,
        )
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
