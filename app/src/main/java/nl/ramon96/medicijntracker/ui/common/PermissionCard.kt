package nl.ramon96.medicijntracker.ui.common

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.ramon96.medicijntracker.R

/**
 * Shown on the Vandaag screen whenever something would stop reminders from arriving. Each row
 * links straight to the system screen that fixes it.
 */
@Composable
fun PermissionCard(status: PermissionStatus, modifier: Modifier = Modifier) {
    if (status.allGood) return
    val context = LocalContext.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) openNotificationSettings(context)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null)
                Text(
                    text = stringResource(R.string.permission_card_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(
                text = stringResource(R.string.permission_card_body),
                style = MaterialTheme.typography.bodyMedium,
            )

            if (!status.notificationsEnabled) {
                TextButton(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        openNotificationSettings(context)
                    }
                }) {
                    Text(stringResource(R.string.permission_fix_notifications))
                }
            }

            if (!status.exactAlarmsAllowed) {
                TextButton(onClick = { openExactAlarmSettings(context) }) {
                    Text(stringResource(R.string.permission_fix_exact_alarms))
                }
            }

            if (!status.ignoringBatteryOptimisations) {
                TextButton(onClick = { requestIgnoreBatteryOptimisations(context) }) {
                    Text(stringResource(R.string.permission_fix_battery))
                }
            }
        }
    }
}
