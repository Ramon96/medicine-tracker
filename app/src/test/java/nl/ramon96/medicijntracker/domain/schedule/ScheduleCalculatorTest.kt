package nl.ramon96.medicijntracker.domain.schedule

import nl.ramon96.medicijntracker.domain.model.DoseTime
import nl.ramon96.medicijntracker.domain.model.Medicine
import nl.ramon96.medicijntracker.domain.model.Schedule
import nl.ramon96.medicijntracker.domain.model.ScheduleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ScheduleCalculatorTest {

    private val start = LocalDate.of(2026, 1, 1)

    private fun medicine(
        schedule: Schedule = Schedule(),
        doseTimes: List<DoseTime> = listOf(DoseTime(time = LocalTime.of(8, 0))),
        startDate: LocalDate = start,
        endDate: LocalDate? = null,
        active: Boolean = true,
    ) = Medicine(
        id = 1,
        name = "Test",
        startDate = startDate,
        endDate = endDate,
        active = active,
        schedule = schedule,
        doseTimes = doseTimes,
    )

    // --- period boundaries -------------------------------------------------

    @Test
    fun `not due before the start date`() {
        val medicine = medicine()
        assertFalse(ScheduleCalculator.isDueOn(medicine, start.minusDays(1)))
        assertTrue(ScheduleCalculator.isDueOn(medicine, start))
    }

    @Test
    fun `not due after the end date`() {
        val medicine = medicine(endDate = start.plusDays(9))
        assertTrue(ScheduleCalculator.isDueOn(medicine, start.plusDays(9)))
        assertFalse(ScheduleCalculator.isDueOn(medicine, start.plusDays(10)))
    }

    @Test
    fun `inactive medicine is never due`() {
        assertFalse(ScheduleCalculator.isDueOn(medicine(active = false), start))
    }

    // --- daily / interval / weekdays ---------------------------------------

    @Test
    fun `daily is due every day`() {
        val medicine = medicine()
        (0L..40L).forEach { assertTrue(ScheduleCalculator.isDueOn(medicine, start.plusDays(it))) }
    }

    @Test
    fun `interval of three days stays aligned across a month boundary`() {
        val medicine = medicine(
            schedule = Schedule(type = ScheduleType.INTERVAL_DAYS, intervalDays = 3),
        )
        val due = (0L..40L)
            .map { start.plusDays(it) }
            .filter { ScheduleCalculator.isDueOn(medicine, it) }

        assertEquals(start, due.first())
        assertTrue(due.contains(LocalDate.of(2026, 1, 31)))
        assertFalse(due.contains(LocalDate.of(2026, 2, 1)))
        assertTrue(due.contains(LocalDate.of(2026, 2, 3)))
        // Every gap is exactly three days, including over the month change.
        due.zipWithNext { a, b -> assertEquals(3, java.time.temporal.ChronoUnit.DAYS.between(a, b)) }
    }

    @Test
    fun `weekday schedule only fires on the selected days`() {
        val medicine = medicine(
            schedule = Schedule(
                type = ScheduleType.WEEKDAYS,
                weekdayMask = Schedule.maskOf(listOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)),
            ),
        )
        // 2026-01-05 is a Monday.
        assertTrue(ScheduleCalculator.isDueOn(medicine, LocalDate.of(2026, 1, 5)))
        assertFalse(ScheduleCalculator.isDueOn(medicine, LocalDate.of(2026, 1, 6)))
        assertTrue(ScheduleCalculator.isDueOn(medicine, LocalDate.of(2026, 1, 8)))
        assertFalse(ScheduleCalculator.isDueOn(medicine, LocalDate.of(2026, 1, 11)))
    }

    @Test
    fun `weekday mask round-trips`() {
        val days = listOf(DayOfWeek.TUESDAY, DayOfWeek.SUNDAY)
        assertEquals(days, Schedule.daysOf(Schedule.maskOf(days)))
    }

    // --- the contraceptive pill: 21 on, 7 off ------------------------------

    private val pill = medicine(
        schedule = Schedule(
            type = ScheduleType.CYCLE,
            cycleActiveDays = 21,
            cyclePauseDays = 7,
            cycleAnchorDate = start,
        ),
    )

    @Test
    fun `cycle is due on the active days and not during the pause`() {
        (0L until 21L).forEach {
            assertTrue("day $it should be active", ScheduleCalculator.isDueOn(pill, start.plusDays(it)))
        }
        (21L until 28L).forEach {
            assertFalse("day $it should be a pause day", ScheduleCalculator.isDueOn(pill, start.plusDays(it)))
        }
    }

    @Test
    fun `cycle repeats over several months`() {
        // Second cycle starts on day 28, third on day 56.
        assertTrue(ScheduleCalculator.isDueOn(pill, start.plusDays(28)))
        assertTrue(ScheduleCalculator.isDueOn(pill, start.plusDays(48)))
        assertFalse(ScheduleCalculator.isDueOn(pill, start.plusDays(49)))
        assertTrue(ScheduleCalculator.isDueOn(pill, start.plusDays(56)))
        // A year later the alignment still holds: 365 = 13*28 + 1, so day 1 of a cycle.
        assertEquals(2, ScheduleCalculator.cyclePosition(pill, start.plusDays(365))!!.dayOfCycle)
    }

    @Test
    fun `cycle position reports the day and the countdown to the switch`() {
        val onDayOne = ScheduleCalculator.cyclePosition(pill, start)!!
        assertEquals(1, onDayOne.dayOfCycle)
        assertEquals(28, onDayOne.cycleLength)
        assertFalse(onDayOne.isPauseDay)
        assertEquals(21, onDayOne.daysUntilSwitch)

        val lastActive = ScheduleCalculator.cyclePosition(pill, start.plusDays(20))!!
        assertEquals(21, lastActive.dayOfCycle)
        assertEquals(1, lastActive.daysUntilSwitch)

        val firstPause = ScheduleCalculator.cyclePosition(pill, start.plusDays(21))!!
        assertTrue(firstPause.isPauseDay)
        assertEquals(7, firstPause.daysUntilSwitch)
    }

    @Test
    fun `cycle anchored before the start date still lines up`() {
        // She started the strip 10 days before she installed the app.
        val anchor = start.minusDays(10)
        val medicine = medicine(
            schedule = Schedule(
                type = ScheduleType.CYCLE,
                cycleActiveDays = 21,
                cyclePauseDays = 7,
                cycleAnchorDate = anchor,
            ),
        )
        assertEquals(11, ScheduleCalculator.cyclePosition(medicine, start)!!.dayOfCycle)
        // Day 21 of the strip = 10 days after the app start date; the pause begins the day after.
        assertTrue(ScheduleCalculator.isDueOn(medicine, start.plusDays(10)))
        assertFalse(ScheduleCalculator.isDueOn(medicine, start.plusDays(11)))
    }

    @Test
    fun `placebo strips keep reminding during the pause`() {
        val withPlacebo = medicine(
            schedule = pill.schedule.copy(remindOnPauseDays = true),
        )
        assertTrue(ScheduleCalculator.isDueOn(withPlacebo, start.plusDays(23)))
        assertTrue(ScheduleCalculator.dosesOn(withPlacebo, start.plusDays(23)).single().isPauseDay)
    }

    @Test
    fun `cycle position is null for other schedule types`() {
        assertNull(ScheduleCalculator.cyclePosition(medicine(), start))
    }

    // --- dose generation ---------------------------------------------------

    @Test
    fun `doses on a day are returned in time order`() {
        val medicine = medicine(
            doseTimes = listOf(
                DoseTime(time = LocalTime.of(22, 0), amount = 2.0),
                DoseTime(time = LocalTime.of(8, 30), amount = 1.0),
            ),
        )
        val doses = ScheduleCalculator.dosesOn(medicine, start)
        assertEquals(listOf(LocalTime.of(8, 30), LocalTime.of(22, 0)), doses.map { it.scheduledAt.toLocalTime() })
        assertEquals(1.0, doses.first().amount, 0.0)
        assertEquals(2.0, doses.last().amount, 0.0)
    }

    @Test
    fun `next dose skips moments that already passed today`() {
        val medicine = medicine(
            doseTimes = listOf(
                DoseTime(time = LocalTime.of(8, 0)),
                DoseTime(time = LocalTime.of(20, 0)),
            ),
        )
        val next = ScheduleCalculator.nextDoses(
            medicine,
            after = LocalDateTime.of(start, LocalTime.of(9, 0)),
        ).single()
        assertEquals(LocalDateTime.of(start, LocalTime.of(20, 0)), next.scheduledAt)
    }

    @Test
    fun `next dose after the pause is the first day of the new strip`() {
        val next = ScheduleCalculator.nextDoses(
            pill,
            after = LocalDateTime.of(start.plusDays(20), LocalTime.of(12, 0)),
        ).single()
        assertEquals(LocalDateTime.of(start.plusDays(28), LocalTime.of(8, 0)), next.scheduledAt)
    }

    @Test
    fun `next doses stop at the end date instead of looping`() {
        val medicine = medicine(endDate = start.plusDays(2))
        val next = ScheduleCalculator.nextDoses(
            medicine,
            after = LocalDateTime.of(start, LocalTime.of(9, 0)),
            limit = 10,
        )
        assertEquals(2, next.size)
    }

    @Test
    fun `medicine without dose times produces nothing`() {
        val medicine = medicine(doseTimes = emptyList())
        assertTrue(ScheduleCalculator.dosesOn(medicine, start).isEmpty())
        assertTrue(ScheduleCalculator.nextDoses(medicine, LocalDateTime.of(start, LocalTime.MIDNIGHT)).isEmpty())
    }

    // --- consumption rate --------------------------------------------------

    @Test
    fun `average use per day accounts for the schedule`() {
        assertEquals(1.0, ScheduleCalculator.averageUnitsPerDay(medicine()), 0.0001)

        val everyOtherDay = medicine(schedule = Schedule(type = ScheduleType.INTERVAL_DAYS, intervalDays = 2))
        assertEquals(0.5, ScheduleCalculator.averageUnitsPerDay(everyOtherDay), 0.0001)

        val twiceWeekly = medicine(
            schedule = Schedule(
                type = ScheduleType.WEEKDAYS,
                weekdayMask = Schedule.maskOf(listOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)),
            ),
        )
        assertEquals(2 / 7.0, ScheduleCalculator.averageUnitsPerDay(twiceWeekly), 0.0001)

        // 21 pills per 28 days, not 28 - this is what keeps the refill forecast honest.
        assertEquals(21 / 28.0, ScheduleCalculator.averageUnitsPerDay(pill), 0.0001)

        val placebo = medicine(schedule = pill.schedule.copy(remindOnPauseDays = true))
        assertEquals(1.0, ScheduleCalculator.averageUnitsPerDay(placebo), 0.0001)
    }

    @Test
    fun `average use adds up multiple doses per day`() {
        val medicine = medicine(
            doseTimes = listOf(
                DoseTime(time = LocalTime.of(8, 0), amount = 1.0),
                DoseTime(time = LocalTime.of(20, 0), amount = 0.5),
            ),
        )
        assertEquals(1.5, ScheduleCalculator.averageUnitsPerDay(medicine), 0.0001)
    }
}
