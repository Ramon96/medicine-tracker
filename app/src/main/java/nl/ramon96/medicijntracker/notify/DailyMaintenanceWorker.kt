package nl.ramon96.medicijntracker.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import nl.ramon96.medicijntracker.MedicijnApp
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The nightly safety net: generates the coming days' doses, closes off yesterday, extends the
 * alarm window and re-checks every refill forecast.
 *
 * Exact alarms handle the reminders themselves; this worker exists so the app recovers on its
 * own if the phone dropped an alarm, was off for a while, or the user never opens the app.
 */
class DailyMaintenanceWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = MedicijnApp.containerOf(applicationContext)
        return try {
            container.reminderCoordinator.refreshAll()
            container.reminderCoordinator.checkRefills(LocalDate.now())
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "dagelijks_onderhoud"

        /** Just after midnight, so a new day is prepared before the first morning reminder. */
        private val RUN_AT: LocalTime = LocalTime.of(0, 5)

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailyMaintenanceWorker>(Duration.ofDays(1))
                .setInitialDelay(initialDelay())
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                // Keep an already-scheduled run instead of pushing it back every app start.
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        private fun initialDelay(): Duration {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(RUN_AT)
            if (!next.isAfter(now)) next = next.plusDays(1)
            return Duration.between(now, next)
        }
    }
}
