package nl.ramon96.medicijntracker.domain.model

import java.time.DayOfWeek
import java.time.LocalDate

enum class ScheduleType {
    /** Every day. */
    DAILY,

    /** Every N days, counted from the medicine's start date. */
    INTERVAL_DAYS,

    /** On specific weekdays. */
    WEEKDAYS,

    /** N days on, M days off, repeating - e.g. a 21/7 contraceptive pill. */
    CYCLE,
}

/**
 * When a medicine is due. Only the fields belonging to [type] are meaningful; the rest keep
 * their defaults so switching schedule type in the editor never loses the other settings.
 */
data class Schedule(
    val type: ScheduleType = ScheduleType.DAILY,
    val intervalDays: Int = 2,
    val weekdayMask: Int = ALL_WEEKDAYS,
    val cycleActiveDays: Int = 21,
    val cyclePauseDays: Int = 7,
    /** First day of an active stretch. Falls back to the medicine's start date when null. */
    val cycleAnchorDate: LocalDate? = null,
    /**
     * True for strips that contain placebo pills: the user still gets a reminder during the
     * pause days and those pills still come out of her stock.
     */
    val remindOnPauseDays: Boolean = false,
) {
    val cycleLength: Int get() = cycleActiveDays + cyclePauseDays

    companion object {
        const val ALL_WEEKDAYS = 0b111_1111

        fun maskOf(days: Iterable<DayOfWeek>): Int =
            days.fold(0) { mask, day -> mask or bitFor(day) }

        fun daysOf(mask: Int): List<DayOfWeek> =
            DayOfWeek.entries.filter { mask and bitFor(it) != 0 }

        fun bitFor(day: DayOfWeek): Int = 1 shl (day.value - 1)
    }
}
