package nl.ramon96.medicijntracker.domain.model

import java.time.LocalDate

/**
 * Everything needed to warn the user in time to order a new batch.
 *
 * [leadTimeDays] is the part that matters most: some medicines take a week or more to arrive
 * from the pharmacy, so the "order now" moment sits that many days before the stock actually
 * runs out.
 */
data class StockInfo(
    /** Units currently in the house. */
    val count: Double = 0.0,
    /** Units in one package, used by the "new package received" button. */
    val unitsPerPackage: Double = 0.0,
    /** How long the pharmacy takes to deliver. */
    val leadTimeDays: Int = 0,
    /** Extra safety margin on top of the lead time. */
    val bufferDays: Int = 3,
    val trackingEnabled: Boolean = false,
    /** Last day an "order now" notification was shown, used to avoid nagging daily. */
    val lastAlertDate: LocalDate? = null,
)

/** Notification behaviour per medicine. */
data class ReminderSettings(
    val enabled: Boolean = true,
    val snoozeMinutes: Int = 15,
    /** Remind once more if the first notification is ignored. */
    val repeatIfIgnored: Boolean = true,
    /**
     * Custom notification sound. Notification channels are immutable once created, so changing
     * this also bumps [channelVersion] and the app registers a fresh channel.
     */
    val soundUri: String? = null,
    val channelVersion: Int = 1,
)
