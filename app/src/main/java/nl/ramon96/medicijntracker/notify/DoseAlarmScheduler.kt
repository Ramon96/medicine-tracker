package nl.ramon96.medicijntracker.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.getSystemService
import nl.ramon96.medicijntracker.domain.model.Intake
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Arms the actual clock alarms.
 *
 * Exact alarms are used on purpose: a medication reminder that drifts by twenty minutes because
 * the phone decided to batch it is not a reminder. Android allows this for medication apps, and
 * the manifest declares USE_EXACT_ALARM for API 33+ so no permission prompt is needed.
 */
class DoseAlarmScheduler(private val context: Context) {

    private val alarmManager: AlarmManager? = context.getSystemService()

    fun canScheduleExact(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager?.canScheduleExactAlarms() == true
        } else {
            true
        }

    /** Arms the reminder for one dose. Moments in the past are ignored. */
    fun schedule(intake: Intake) {
        val manager = alarmManager ?: return
        val triggerAt = intake.remindAt
        if (triggerAt.isBefore(LocalDateTime.now())) return

        val pendingIntent = dosePendingIntent(intake.id, DoseAlarmReceiver.ACTION_DOSE_DUE)
        setAlarm(manager, triggerAt, pendingIntent)
    }

    /**
     * Arms the single follow-up nudge for a dose that was left untouched. The receiver checks
     * the dose is still open before showing anything, so a stale follow-up is harmless.
     */
    fun scheduleFollowUp(intake: Intake, afterMinutes: Int) {
        val manager = alarmManager ?: return
        val triggerAt = LocalDateTime.now().plusMinutes(afterMinutes.toLong())
        setAlarm(manager, triggerAt, dosePendingIntent(intake.id, DoseAlarmReceiver.ACTION_DOSE_FOLLOW_UP))
    }

    fun cancel(intakeId: Long) {
        val manager = alarmManager ?: return
        manager.cancel(dosePendingIntent(intakeId, DoseAlarmReceiver.ACTION_DOSE_DUE))
        manager.cancel(dosePendingIntent(intakeId, DoseAlarmReceiver.ACTION_DOSE_FOLLOW_UP))
    }

    private fun setAlarm(manager: AlarmManager, triggerAt: LocalDateTime, pendingIntent: PendingIntent) {
        val triggerMillis = triggerAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        try {
            if (canScheduleExact()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            } else {
                // Better a slightly late reminder than none at all.
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Kon geen exacte alarm zetten, val terug op inexact", e)
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
        }
    }

    private fun dosePendingIntent(intakeId: Long, action: String): PendingIntent {
        val intent = Intent(context, DoseAlarmReceiver::class.java).apply {
            this.action = action
            putExtra(DoseAlarmReceiver.EXTRA_INTAKE_ID, intakeId)
            // Request code alone decides identity for PendingIntent equality; the data uri keeps
            // intents for different doses distinct as well.
            data = android.net.Uri.parse("medicijn://dose/$intakeId/$action")
        }
        return PendingIntent.getBroadcast(
            context,
            requestCodeFor(intakeId, action),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun requestCodeFor(intakeId: Long, action: String): Int {
        val base = intakeId.toInt() and 0x3FFF_FFFF
        return if (action == DoseAlarmReceiver.ACTION_DOSE_FOLLOW_UP) base or FOLLOW_UP_FLAG else base
    }

    companion object {
        private const val TAG = "DoseAlarmScheduler"
        private const val FOLLOW_UP_FLAG = 0x4000_0000

        /** How long after the reminder the single follow-up nudge is shown. */
        const val FOLLOW_UP_MINUTES = 30
    }
}
