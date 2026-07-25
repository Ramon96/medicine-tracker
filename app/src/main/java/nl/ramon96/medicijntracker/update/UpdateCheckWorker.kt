package nl.ramon96.medicijntracker.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import nl.ramon96.medicijntracker.MainActivity
import nl.ramon96.medicijntracker.MedicijnApp
import nl.ramon96.medicijntracker.R
import nl.ramon96.medicijntracker.notify.NotificationChannels
import java.time.Duration

/**
 * Weekly check for a new release. Deliberately quiet: it only posts a notification when there is
 * genuinely something newer, and never downloads anything on its own.
 */
class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = MedicijnApp.containerOf(applicationContext)
        if (!container.settings.autoUpdateCheck.first()) return Result.success()

        return when (val result = container.updateChecker.check()) {
            is UpdateResult.Available -> {
                notify(result.info)
                Result.success()
            }

            is UpdateResult.UpToDate -> Result.success()
            // A failed check is not worth bothering the user about; try again next week.
            is UpdateResult.Failed -> Result.success()
        }
    }

    private fun notify(info: UpdateInfo) {
        NotificationChannels.ensureUpdateChannel(applicationContext)

        val openApp = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(
            applicationContext,
            NotificationChannels.UPDATE_CHANNEL_ID,
        )
            .setSmallIcon(R.drawable.ic_stat_medicijn)
            .setContentTitle(applicationContext.getString(R.string.notif_update_title))
            .setContentText(
                applicationContext.getString(R.string.notif_update_text, info.versionName),
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()

        val manager = NotificationManagerCompat.from(applicationContext)
        if (!manager.areNotificationsEnabled()) return
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    companion object {
        private const val UNIQUE_NAME = "update_check"
        private const val NOTIFICATION_ID = 2_000_001

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(Duration.ofDays(7))
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
