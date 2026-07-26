package nl.ramon96.medicijntracker.notify

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import nl.ramon96.medicijntracker.MainActivity
import nl.ramon96.medicijntracker.R
import nl.ramon96.medicijntracker.ui.common.doseLabel
import nl.ramon96.medicijntracker.ui.today.formatAmount
import nl.ramon96.medicijntracker.domain.model.Intake
import nl.ramon96.medicijntracker.domain.model.Medicine
import nl.ramon96.medicijntracker.domain.stock.RefillForecast
import java.time.format.DateTimeFormatter

/** Builds and shows the notifications; both receivers and the workers funnel through here. */
object DoseNotifier {

    private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /** Refill notification ids sit above this so they never collide with dose ids. */
    private const val REFILL_NOTIFICATION_OFFSET = 1_000_000

    fun showDose(context: Context, intake: Intake, medicine: Medicine) {
        val channelId = NotificationChannels.ensureMedicineChannel(context, medicine)

        // "2 × 20 mg", not "20 mg": this is the line someone reads while holding the box.
        val dose = doseLabel(intake.amount, medicine.dosage)
            ?: context.getString(R.string.dose_amount, formatAmount(intake.amount))
        val subtitle = listOfNotNull(
            dose,
            context.getString(R.string.notif_dose_planned_at, intake.scheduledAt.format(timeFormat)),
        ).joinToString(" · ")

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_medicijn)
            .setContentTitle(context.getString(R.string.notif_dose_title, medicine.name))
            .setContentText(subtitle)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setContentIntent(openAppIntent(context))
            .addAction(
                0,
                context.getString(R.string.action_taken),
                actionIntent(context, IntakeActionReceiver.ACTION_TAKEN, intake.id),
            )
            .addAction(
                0,
                context.getString(R.string.action_snooze),
                actionIntent(context, IntakeActionReceiver.ACTION_SNOOZE, intake.id),
            )
            .addAction(
                0,
                context.getString(R.string.action_skip),
                actionIntent(context, IntakeActionReceiver.ACTION_SKIP, intake.id),
            )
            .build()

        notify(context, intake.id.toInt(), notification)
    }

    fun showRefill(context: Context, medicine: Medicine, forecast: RefillForecast) {
        NotificationChannels.ensureRefillChannel(context)

        val days = forecast.daysOfSupply ?: 0
        val text = context.resources.getQuantityString(
            R.plurals.notif_refill_text,
            days,
            days,
            medicine.stock.leadTimeDays,
        )

        val notification = NotificationCompat.Builder(context, NotificationChannels.REFILL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_medicijn)
            .setContentTitle(context.getString(R.string.notif_refill_title, medicine.name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()

        notify(context, REFILL_NOTIFICATION_OFFSET + medicine.id.toInt(), notification)
    }

    fun cancelDose(context: Context, intakeId: Long) {
        NotificationManagerCompat.from(context).cancel(intakeId.toInt())
    }

    private fun notify(context: Context, id: Int, notification: android.app.Notification) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS was revoked between the check and the call; the in-app
            // permission card tells the user how to switch reminders back on.
        }
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun actionIntent(context: Context, action: String, intakeId: Long): PendingIntent {
        val intent = Intent(context, IntakeActionReceiver::class.java).apply {
            this.action = action
            putExtra(IntakeActionReceiver.EXTRA_INTAKE_ID, intakeId)
            data = Uri.parse("medicijn://intake/$intakeId/$action")
        }
        return PendingIntent.getBroadcast(
            context,
            requestCodeFor(intakeId, action),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Two bits of action in the low end, the dose id above it. An explicit mapping rather than a
     * hash, so two buttons on the same notification can never end up sharing a request code.
     */
    private fun requestCodeFor(intakeId: Long, action: String): Int {
        val actionBits = when (action) {
            IntakeActionReceiver.ACTION_TAKEN -> 0
            IntakeActionReceiver.ACTION_SNOOZE -> 1
            IntakeActionReceiver.ACTION_SKIP -> 2
            else -> 3
        }
        return ((intakeId.toInt() and 0x0FFF_FFFF) shl 2) or actionBits
    }
}
