package nl.ramon96.medicijntracker.domain.model

import java.time.LocalDateTime

enum class IntakeStatus {
    /** Due, not acted on yet. */
    PENDING,
    TAKEN,
    /** Deliberately not taken. */
    SKIPPED,
    /** Reminder postponed; a new alarm is armed. */
    SNOOZED,
    /** The day passed without any action. */
    MISSED,
}

/**
 * A dose as recorded in the database. One row per (medicine, scheduled moment); the unique
 * index on those two columns is what makes re-arming alarms after a reboot idempotent.
 */
data class Intake(
    val id: Long = 0,
    val medicineId: Long,
    val scheduledAt: LocalDateTime,
    val actualAt: LocalDateTime? = null,
    val snoozedUntil: LocalDateTime? = null,
    val status: IntakeStatus = IntakeStatus.PENDING,
    val amount: Double = 1.0,
) {
    /** When the reminder for this dose should actually fire. */
    val remindAt: LocalDateTime get() = snoozedUntil ?: scheduledAt

    val isOpen: Boolean get() = status == IntakeStatus.PENDING || status == IntakeStatus.SNOOZED
}

/** An intake joined with the medicine it belongs to, for list screens and the widget. */
data class IntakeWithMedicine(
    val intake: Intake,
    val medicine: Medicine,
)
