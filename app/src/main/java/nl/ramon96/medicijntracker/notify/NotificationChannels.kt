package nl.ramon96.medicijntracker.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import androidx.core.content.getSystemService
import nl.ramon96.medicijntracker.R
import nl.ramon96.medicijntracker.domain.model.Medicine

/**
 * Notification channels, one per medicine.
 *
 * A channel is immutable once Android has seen it: its sound, importance and vibration can only
 * be changed by the user in system settings, never by the app. So the channel id carries a
 * version - changing a medicine's sound bumps the version, which registers a fresh channel and
 * retires the old one. Giving every medicine its own channel also means she can fine-tune each
 * one from Android's own notification settings.
 */
object NotificationChannels {

    const val REFILL_CHANNEL_ID = "refill_v1"
    const val UPDATE_CHANNEL_ID = "updates_v1"

    private const val MEDICINE_CHANNEL_PREFIX = "med_"

    fun channelIdFor(medicine: Medicine): String =
        "$MEDICINE_CHANNEL_PREFIX${medicine.id}_v${medicine.reminders.channelVersion}"

    /** Creates the channel for this medicine and removes any older version of it. */
    fun ensureMedicineChannel(context: Context, medicine: Medicine): String {
        val manager = context.getSystemService<NotificationManager>() ?: return channelIdFor(medicine)
        val channelId = channelIdFor(medicine)

        val channel = NotificationChannel(
            channelId,
            medicine.name.ifBlank { context.getString(R.string.app_name) },
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_medicine_description)
            enableVibration(medicine.reminders.vibrate)
            setShowBadge(true)
            medicine.reminders.soundUri?.let { uri ->
                setSound(
                    Uri.parse(uri),
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build(),
                )
            }
        }
        manager.createNotificationChannel(channel)

        val prefix = "$MEDICINE_CHANNEL_PREFIX${medicine.id}_v"
        manager.notificationChannels
            .filter { it.id.startsWith(prefix) && it.id != channelId }
            .forEach { manager.deleteNotificationChannel(it.id) }

        return channelId
    }

    fun ensureRefillChannel(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        val channel = NotificationChannel(
            REFILL_CHANNEL_ID,
            context.getString(R.string.channel_refill_name),
            // Deliberately lower than a dose reminder: ordering is important but not urgent.
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.channel_refill_description)
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun ensureUpdateChannel(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        val channel = NotificationChannel(
            UPDATE_CHANNEL_ID,
            context.getString(R.string.channel_update_name),
            // A new app version is never urgent enough to interrupt.
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.channel_update_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    /** Drops the channels of a medicine that no longer exists. */
    fun removeChannelsFor(context: Context, medicineId: Long) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        val prefix = "$MEDICINE_CHANNEL_PREFIX${medicineId}_v"
        manager.notificationChannels
            .filter { it.id.startsWith(prefix) }
            .forEach { manager.deleteNotificationChannel(it.id) }
    }
}
