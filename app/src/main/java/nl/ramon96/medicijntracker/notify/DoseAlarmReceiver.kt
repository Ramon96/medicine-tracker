package nl.ramon96.medicijntracker.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import nl.ramon96.medicijntracker.MedicijnApp
import nl.ramon96.medicijntracker.domain.model.IntakeStatus

/** Fires when a dose is due (or when its single follow-up nudge is due). */
class DoseAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val intakeId = intent.getLongExtra(EXTRA_INTAKE_ID, -1L)
        if (intakeId <= 0) return
        val isFollowUp = intent.action == ACTION_DOSE_FOLLOW_UP

        val container = MedicijnApp.containerOf(context)
        goAsyncInScope(this) {
            val intake = container.doseRepository.getById(intakeId) ?: return@goAsyncInScope
            // The dose may have been taken from the widget or the app in the meantime; a stale
            // alarm must not produce a reminder.
            if (!intake.isOpen) return@goAsyncInScope

            val medicine = container.medicineRepository.getById(intake.medicineId) ?: return@goAsyncInScope
            if (!medicine.reminders.enabled) return@goAsyncInScope

            DoseNotifier.showDose(context, intake, medicine)

            if (!isFollowUp && medicine.reminders.repeatIfIgnored && intake.status == IntakeStatus.PENDING) {
                container.alarmScheduler.scheduleFollowUp(intake, DoseAlarmScheduler.FOLLOW_UP_MINUTES)
            }
        }
    }

    companion object {
        const val ACTION_DOSE_DUE = "nl.ramon96.medicijntracker.DOSE_DUE"
        const val ACTION_DOSE_FOLLOW_UP = "nl.ramon96.medicijntracker.DOSE_FOLLOW_UP"
        const val EXTRA_INTAKE_ID = "intake_id"
    }
}
