package nl.ramon96.medicijntracker.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import nl.ramon96.medicijntracker.MedicijnApp

/** Ticks a dose off straight from the widget. */
class MarkTakenAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val intakeId = parameters[intakeIdKey] ?: return
        val container = MedicijnApp.containerOf(context)
        container.doseRepository.markTaken(intakeId)
        // Cancels the pending reminder, clears the notification and refreshes the widget.
        container.reminderCoordinator.onDoseSettled(intakeId)
    }

    companion object {
        val intakeIdKey = ActionParameters.Key<Long>("intakeId")
    }
}
