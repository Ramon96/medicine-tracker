package nl.ramon96.medicijntracker.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.first
import nl.ramon96.medicijntracker.MedicijnApp

/**
 * Handles the buttons on a dose notification, so a dose can be ticked off without opening the
 * app - which is the difference between a reminder that gets used and one that gets swiped away.
 */
class IntakeActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val intakeId = intent.getLongExtra(EXTRA_INTAKE_ID, -1L)
        if (intakeId <= 0) return

        val container = MedicijnApp.containerOf(context)
        goAsyncInScope(this) {
            when (intent.action) {
                ACTION_TAKEN -> {
                    container.doseRepository.markTaken(intakeId)
                    container.reminderCoordinator.onDoseSettled(intakeId)
                }

                ACTION_SKIP -> {
                    container.doseRepository.markSkipped(intakeId)
                    container.reminderCoordinator.onDoseSettled(intakeId)
                }

                ACTION_SNOOZE -> {
                    val intake = container.doseRepository.getById(intakeId)
                    val medicine = intake?.let { container.medicineRepository.getById(it.medicineId) }
                    val minutes = medicine?.reminders?.snoozeMinutes
                        ?: container.settings.defaultSnoozeMinutes.first()
                    container.reminderCoordinator.snooze(intakeId, minutes)
                }
            }
        }
    }

    companion object {
        const val ACTION_TAKEN = "nl.ramon96.medicijntracker.INTAKE_TAKEN"
        const val ACTION_SKIP = "nl.ramon96.medicijntracker.INTAKE_SKIP"
        const val ACTION_SNOOZE = "nl.ramon96.medicijntracker.INTAKE_SNOOZE"
        const val EXTRA_INTAKE_ID = "intake_id"
    }
}
