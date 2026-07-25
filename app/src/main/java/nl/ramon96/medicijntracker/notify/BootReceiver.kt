package nl.ramon96.medicijntracker.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import nl.ramon96.medicijntracker.MedicijnApp

/**
 * Alarms do not survive a reboot, a clock change or a timezone change, so every reminder has to
 * be armed again afterwards. Without this the app silently stops reminding after a restart.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> Unit

            else -> return
        }

        val container = MedicijnApp.containerOf(context)
        goAsyncInScope(this) {
            container.reminderCoordinator.refreshAll()
            DailyMaintenanceWorker.schedule(context)
        }
    }
}
