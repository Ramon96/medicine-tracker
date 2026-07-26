package nl.ramon96.medicijntracker.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import nl.ramon96.medicijntracker.R
import nl.ramon96.medicijntracker.domain.model.Medicine
import nl.ramon96.medicijntracker.domain.model.Schedule
import nl.ramon96.medicijntracker.domain.model.ScheduleType
import nl.ramon96.medicijntracker.domain.schedule.ScheduleCalculator
import nl.ramon96.medicijntracker.ui.today.formatAmount
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

/**
 * What one dose actually is: "2 × 20 mg".
 *
 * Strength and count live in separate fields - the strength is per tablet, the count is how many
 * are taken at once - so showing the strength on its own reads as the whole dose and hides the
 * fact that a count was ever entered.
 *
 * Returns just the strength when one tablet is taken, and falls back to a bare count when no
 * strength was filled in. Amounts that differ per time of day cannot be summed into one line, so
 * those show the strength and leave the per-time counts to the rows that list them.
 */
@Composable
fun doseSummary(medicine: Medicine): String? {
    val strength = medicine.dosage.takeIf { it.isNotBlank() }
    val amounts = medicine.doseTimes.map { it.amount }.distinct()
    val amount = amounts.singleOrNull() ?: return strength

    if (amount == 1.0) return strength
    val count = stringResource(R.string.dose_amount, formatAmount(amount))
    return if (strength == null) count else "${formatAmount(amount)} × $strength"
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
