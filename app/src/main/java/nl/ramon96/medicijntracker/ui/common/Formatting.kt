package nl.ramon96.medicijntracker.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import nl.ramon96.medicijntracker.R
import nl.ramon96.medicijntracker.domain.model.Medicine
import nl.ramon96.medicijntracker.domain.model.Schedule
import nl.ramon96.medicijntracker.domain.model.ScheduleType
import nl.ramon96.medicijntracker.domain.schedule.ScheduleCalculator
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val dutch = Locale.forLanguageTag("nl-NL")

val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", dutch)
val shortDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", dutch)

/** One-line description of when a medicine is due, for list rows. */
@Composable
fun scheduleSummary(medicine: Medicine): String {
    val times = medicine.doseTimes.joinToString(", ") { it.time.format(timeFormatter) }
    val pattern = when (medicine.schedule.type) {
        ScheduleType.DAILY -> stringResource(R.string.schedule_daily)

        ScheduleType.INTERVAL_DAYS -> pluralStringResource(
            R.plurals.schedule_interval,
            medicine.schedule.intervalDays,
            medicine.schedule.intervalDays,
        )

        ScheduleType.WEEKDAYS -> Schedule.daysOf(medicine.schedule.weekdayMask)
            .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, dutch) }
            .ifBlank { stringResource(R.string.schedule_no_days) }

        ScheduleType.CYCLE -> stringResource(
            R.string.schedule_cycle,
            medicine.schedule.cycleActiveDays,
            medicine.schedule.cyclePauseDays,
        )
    }
    return if (times.isBlank()) pattern else "$pattern · $times"
}

/** "Dag 14 van 28 · nog 7 dagen slikken", shown for cycle medicines. */
@Composable
fun cycleSummary(medicine: Medicine, today: LocalDate): String? {
    val position = ScheduleCalculator.cyclePosition(medicine, today) ?: return null
    val phase = if (position.isPauseDay) {
        pluralStringResource(R.plurals.cycle_pause_left, position.daysUntilSwitch, position.daysUntilSwitch)
    } else {
        pluralStringResource(R.plurals.cycle_active_left, position.daysUntilSwitch, position.daysUntilSwitch)
    }
    return stringResource(R.string.cycle_day_of, position.dayOfCycle, position.cycleLength) + " · " + phase
}
