package nl.ramon96.medicijntracker.domain.schedule

import nl.ramon96.medicijntracker.domain.model.Medicine
import nl.ramon96.medicijntracker.domain.model.PlannedDose
import nl.ramon96.medicijntracker.domain.model.Schedule
import nl.ramon96.medicijntracker.domain.model.ScheduleType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** Where a date sits inside a cycle schedule, used for both dosing and the UI subtitle. */
data class CyclePosition(
    /** 1-based day within the whole cycle. */
    val dayOfCycle: Int,
    val cycleLength: Int,
    val isPauseDay: Boolean,
    /** Days until the next switch between taking and pausing. */
    val daysUntilSwitch: Int,
)

/**
 * Decides on which days and at which times a medicine is due.
 *
 * Deliberately free of Android dependencies so the awkward parts - 21/7 cycles crossing month
 * boundaries, interval alignment, end dates - are covered by plain JVM unit tests.
 */
object ScheduleCalculator {

    /** True when [date] falls inside the medicine's active period at all. */
    fun isWithinPeriod(medicine: Medicine, date: LocalDate): Boolean {
        if (!medicine.active) return false
        if (date.isBefore(medicine.startDate)) return false
        val end = medicine.endDate
        return end == null || !date.isAfter(end)
    }

    /** True when a dose is due on [date] (pause days count only if the user wants reminders then). */
    fun isDueOn(medicine: Medicine, date: LocalDate): Boolean {
        if (!isWithinPeriod(medicine, date)) return false
        return when (medicine.schedule.type) {
            ScheduleType.DAILY -> true

            ScheduleType.INTERVAL_DAYS -> {
                val interval = medicine.schedule.intervalDays.coerceAtLeast(1)
                ChronoUnit.DAYS.between(medicine.startDate, date) % interval == 0L
            }

            ScheduleType.WEEKDAYS ->
                medicine.schedule.weekdayMask and Schedule.bitFor(date.dayOfWeek) != 0

            ScheduleType.CYCLE -> {
                val position = cyclePosition(medicine, date) ?: return false
                !position.isPauseDay || medicine.schedule.remindOnPauseDays
            }
        }
    }

    /**
     * Where [date] sits in the cycle, or null when this medicine does not use a cycle schedule
     * (or the cycle is misconfigured to zero length).
     */
    fun cyclePosition(medicine: Medicine, date: LocalDate): CyclePosition? {
        val schedule = medicine.schedule
        if (schedule.type != ScheduleType.CYCLE) return null
        val length = schedule.cycleLength
        if (length <= 0) return null

        val anchor = schedule.cycleAnchorDate ?: medicine.startDate
        // floorMod keeps the cycle aligned for dates before the anchor as well.
        val index = Math.floorMod(ChronoUnit.DAYS.between(anchor, date), length)
        val isPauseDay = index >= schedule.cycleActiveDays
        val daysUntilSwitch = if (isPauseDay) length - index else schedule.cycleActiveDays - index
        return CyclePosition(
            dayOfCycle = index + 1,
            cycleLength = length,
            isPauseDay = isPauseDay,
            daysUntilSwitch = daysUntilSwitch,
        )
    }

    /** Every dose due on [date], in chronological order. Empty when the medicine is not due. */
    fun dosesOn(medicine: Medicine, date: LocalDate): List<PlannedDose> {
        if (!isDueOn(medicine, date)) return emptyList()
        val isPauseDay = cyclePosition(medicine, date)?.isPauseDay ?: false
        return medicine.doseTimes
            .sortedBy { it.time }
            .map { doseTime ->
                PlannedDose(
                    medicineId = medicine.id,
                    scheduledAt = LocalDateTime.of(date, doseTime.time),
                    amount = doseTime.amount,
                    isPauseDay = isPauseDay,
                )
            }
    }

    /**
     * The next [limit] doses strictly after [after]. Scans forward day by day and gives up after
     * [maxDaysAhead] so a medicine whose period has ended can never loop forever.
     */
    fun nextDoses(
        medicine: Medicine,
        after: LocalDateTime,
        limit: Int = 1,
        maxDaysAhead: Int = 400,
    ): List<PlannedDose> {
        if (limit <= 0 || medicine.doseTimes.isEmpty()) return emptyList()
        val result = mutableListOf<PlannedDose>()
        var date = after.toLocalDate()
        var daysScanned = 0
        while (result.size < limit && daysScanned <= maxDaysAhead) {
            medicine.endDate?.let { if (date.isAfter(it)) return result }
            dosesOn(medicine, date)
                .filter { it.scheduledAt.isAfter(after) }
                .forEach { if (result.size < limit) result += it }
            date = date.plusDays(1)
            daysScanned++
        }
        return result
    }

    /**
     * Average units consumed per calendar day, averaged over a full schedule period.
     *
     * This is what makes the refill forecast correct for cycle schedules: a 21/7 pill uses 21
     * units per 28 days, not 28, so a naive "units per day" would order new strips too early.
     */
    fun averageUnitsPerDay(medicine: Medicine): Double {
        val perDosingDay = medicine.unitsPerDosingDay
        if (perDosingDay <= 0.0) return 0.0
        return when (medicine.schedule.type) {
            ScheduleType.DAILY -> perDosingDay

            ScheduleType.INTERVAL_DAYS ->
                perDosingDay / medicine.schedule.intervalDays.coerceAtLeast(1)

            ScheduleType.WEEKDAYS -> {
                val activeDays = Integer.bitCount(medicine.schedule.weekdayMask and Schedule.ALL_WEEKDAYS)
                perDosingDay * activeDays / 7.0
            }

            ScheduleType.CYCLE -> {
                val schedule = medicine.schedule
                val length = schedule.cycleLength
                if (length <= 0) return 0.0
                // Placebo strips are consumed on pause days too, so those days count only when
                // the user asked to be reminded during the pause.
                val consumingDays =
                    if (schedule.remindOnPauseDays) length else schedule.cycleActiveDays
                perDosingDay * consumingDays / length.toDouble()
            }
        }
    }
}
