package nl.ramon96.medicijntracker.notify

import android.content.Context
import nl.ramon96.medicijntracker.data.prefs.AppSettings
import nl.ramon96.medicijntracker.data.repo.DoseRepository
import nl.ramon96.medicijntracker.data.repo.MedicineRepository
import nl.ramon96.medicijntracker.domain.stock.StockForecaster
import nl.ramon96.medicijntracker.widget.TodayWidget
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The single place that keeps alarms, notifications, generated doses and the widget in step.
 *
 * Everything that changes state - the UI, the notification actions, the boot receiver, the daily
 * worker - calls in here rather than touching the alarm manager directly, so there is one
 * definition of "what should currently be armed".
 */
class ReminderCoordinator(
    private val context: Context,
    private val medicineRepository: MedicineRepository,
    private val doseRepository: DoseRepository,
    private val alarmScheduler: DoseAlarmScheduler,
    private val settings: AppSettings,
) {

    private val armedAlarms = ArmedAlarmRegistry(context)

    /**
     * Brings everything up to date: generates the coming days, closes off yesterday, re-arms the
     * alarm window and refreshes the widget. Safe and cheap enough to call on app start, after a
     * reboot and once a day.
     */
    suspend fun refreshAll() {
        val now = LocalDateTime.now()
        doseRepository.generateUpcoming(now.toLocalDate(), daysAhead = GENERATE_DAYS_AHEAD)
        doseRepository.markOverdueAsMissed(now.toLocalDate().atStartOfDay())
        rearmAlarms(now)
        checkRefills(now.toLocalDate())
        TodayWidget.refresh(context)
    }

    /** Re-arms exactly the doses that are still open inside the alarm window. */
    private suspend fun rearmAlarms(now: LocalDateTime) {
        val open = doseRepository.getOpenBetween(now, now.plusHours(ALARM_WINDOW_HOURS))
        val medicines = medicineRepository.getAll().associateBy { it.id }

        val toArm = open.filter { medicines[it.medicineId]?.reminders?.enabled == true }
        val armedIds = toArm.map { it.id }.toSet()

        // Anything armed earlier that is no longer due (taken, skipped, rescheduled, deleted)
        // has to lose its alarm, otherwise a stale reminder pops up later.
        (armedAlarms.armedIds() - armedIds).forEach { alarmScheduler.cancel(it) }

        toArm.forEach { alarmScheduler.schedule(it) }
        armedAlarms.replace(armedIds)
    }

    /** Called after a medicine was added or edited, so its doses reflect the new schedule. */
    suspend fun onMedicineSaved(medicineId: Long) {
        doseRepository.clearFuturePending(medicineId)
        refreshAll()
    }

    suspend fun onMedicineDeleted(medicineId: Long) {
        NotificationChannels.removeChannelsFor(context, medicineId)
        refreshAll()
    }

    /** Called after a dose was taken, skipped or undone from anywhere in the app. */
    suspend fun onDoseSettled(intakeId: Long) {
        alarmScheduler.cancel(intakeId)
        DoseNotifier.cancelDose(context, intakeId)
        armedAlarms.remove(intakeId)
        checkRefills(LocalDate.now())
        TodayWidget.refresh(context)
    }

    /** Postpones a dose and arms the new moment. */
    suspend fun snooze(intakeId: Long, minutes: Int) {
        val until = LocalDateTime.now().plusMinutes(minutes.toLong())
        doseRepository.markSnoozed(intakeId, until)
        DoseNotifier.cancelDose(context, intakeId)
        doseRepository.getById(intakeId)?.let {
            alarmScheduler.schedule(it)
            armedAlarms.add(intakeId)
        }
        TodayWidget.refresh(context)
    }

    /**
     * Warns about medicines that have to be ordered now, taking the pharmacy lead time into
     * account. Throttled per medicine so it does not repeat every day.
     */
    suspend fun checkRefills(today: LocalDate) {
        val throttleDays = settings.currentRefillThrottleDays()
        medicineRepository.getAll().forEach { medicine ->
            val forecast = StockForecaster.forecast(medicine, today)
            if (StockForecaster.shouldAlert(medicine, today, forecast, throttleDays)) {
                DoseNotifier.showRefill(context, medicine, forecast)
                medicineRepository.markRefillAlerted(medicine.id, today)
            }
        }
    }

    companion object {
        /** Alarms are armed this far ahead; the daily worker extends the window every night. */
        const val ALARM_WINDOW_HOURS = 48L
        const val GENERATE_DAYS_AHEAD = 2
    }
}
