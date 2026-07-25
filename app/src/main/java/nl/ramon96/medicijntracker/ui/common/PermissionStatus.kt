package nl.ramon96.medicijntracker.ui.common

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * The three system settings that decide whether reminders actually arrive.
 *
 * On many phones (Samsung and Xiaomi especially) an app that is not exempt from battery
 * optimisation simply stops firing alarms after a while, which looks exactly like a broken app.
 * Surfacing these three up front is the difference between a tracker she can rely on and one
 * that quietly misses a dose.
 */
data class PermissionStatus(
    val notificationsEnabled: Boolean,
    val exactAlarmsAllowed: Boolean,
    val ignoringBatteryOptimisations: Boolean,
) {
    val allGood: Boolean
        get() = notificationsEnabled && exactAlarmsAllowed && ignoringBatteryOptimisations
}

fun readPermissionStatus(context: Context): PermissionStatus {
    val exactAllowed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService<AlarmManager>()?.canScheduleExactAlarms() ?: false
    } else {
        true
    }
    val ignoringBattery = context.getSystemService<PowerManager>()
        ?.isIgnoringBatteryOptimizations(context.packageName) ?: false

    return PermissionStatus(
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        exactAlarmsAllowed = exactAllowed,
        ignoringBatteryOptimisations = ignoringBattery,
    )
}

/** Re-reads the status every time the screen comes back to the foreground. */
@Composable
fun rememberPermissionStatus(): PermissionStatus {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var status by remember { mutableStateOf(readPermissionStatus(context)) }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) status = readPermissionStatus(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return status
}

fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        .setData(Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

@Suppress("BatteryLife") // A missed dose reminder is exactly the case this exemption is for.
fun requestIgnoreBatteryOptimisations(context: Context) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        .setData(Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.onFailure {
        context.startActivity(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
