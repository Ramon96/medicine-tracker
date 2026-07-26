package nl.ramon96.medicijntracker.ui.medicines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nl.ramon96.medicijntracker.di.AppContainer
import nl.ramon96.medicijntracker.domain.model.DoseTime
import nl.ramon96.medicijntracker.domain.model.Medicine
import nl.ramon96.medicijntracker.domain.model.Schedule
import nl.ramon96.medicijntracker.domain.model.ScheduleType
import nl.ramon96.medicijntracker.domain.stock.ExpiryWatcher
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * What a scan already knows before the form opens.
 *
 * Passed as navigation arguments rather than held in shared state, so returning to the form after
 * a process death cannot resurrect a stale scan.
 */
data class ScanPrefill(
    val gtin: String? = null,
    val expiry: LocalDate? = null,
    val name: String? = null,
    val dosage: String? = null,
) {
    val isEmpty: Boolean get() = gtin == null && expiry == null && name == null && dosage == null
}

/** The add/edit form state: one editable copy of the medicine plus validation. */
data class MedicineEditState(
    val draft: Medicine,
    val isNew: Boolean,
    val loading: Boolean = false,
    val saved: Boolean = false,
) {
    val nameError: Boolean get() = draft.name.isBlank()
    val noDoseTimes: Boolean get() = draft.doseTimes.isEmpty()
    val canSave: Boolean get() = !nameError && !noDoseTimes
}

class MedicineEditViewModel(
    private val container: AppContainer,
    private val medicineId: Long,
    private val prefill: ScanPrefill? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(
        MedicineEditState(
            draft = emptyDraft().withPrefill(prefill),
            isNew = medicineId == 0L,
            loading = medicineId != 0L,
        ),
    )
    val state: StateFlow<MedicineEditState> = _state.asStateFlow()

    /** What was loaded from the database, so save can tell which channel settings changed. */
    private var original: Medicine? = null

    init {
        if (medicineId != 0L) {
            viewModelScope.launch {
                container.medicineRepository.getById(medicineId)?.let { medicine ->
                    // original stays un-prefilled: it is what is in the database, and save()
                    // compares against it to decide whether the notification channel changed.
                    original = medicine
                    _state.value = MedicineEditState(draft = medicine.withPrefill(prefill), isNew = false)
                }
            }
        }
    }

    private fun edit(block: Medicine.() -> Medicine) {
        _state.value = _state.value.copy(draft = _state.value.draft.block())
    }

    fun setName(value: String) = edit { copy(name = value) }
    fun setDosage(value: String) = edit { copy(dosage = value) }
    fun setNotes(value: String) = edit { copy(notes = value) }
    fun setActive(value: Boolean) = edit { copy(active = value) }
    fun setStartDate(value: LocalDate) = edit { copy(startDate = value) }
    fun setEndDate(value: LocalDate?) = edit { copy(endDate = value) }

    // --- schedule ----------------------------------------------------------

    fun setScheduleType(type: ScheduleType) = edit {
        val anchor = schedule.cycleAnchorDate ?: startDate
        copy(schedule = schedule.copy(type = type, cycleAnchorDate = anchor))
    }

    fun setIntervalDays(days: Int) = edit {
        copy(schedule = schedule.copy(intervalDays = days.coerceIn(1, 365)))
    }

    fun toggleWeekday(day: DayOfWeek) = edit {
        val bit = Schedule.bitFor(day)
        copy(schedule = schedule.copy(weekdayMask = schedule.weekdayMask xor bit))
    }

    fun setCycleDays(activeDays: Int, pauseDays: Int) = edit {
        copy(
            schedule = schedule.copy(
                cycleActiveDays = activeDays.coerceIn(1, 365),
                cyclePauseDays = pauseDays.coerceIn(0, 365),
            ),
        )
    }

    fun setCycleAnchor(date: LocalDate) = edit {
        copy(schedule = schedule.copy(cycleAnchorDate = date))
    }

    fun setRemindOnPauseDays(value: Boolean) = edit {
        copy(schedule = schedule.copy(remindOnPauseDays = value))
    }

    // --- dose times --------------------------------------------------------

    fun addDoseTime(time: LocalTime) = edit {
        if (doseTimes.any { it.time == time }) this
        else copy(doseTimes = (doseTimes + DoseTime(time = time)).sortedBy { it.time })
    }

    fun updateDoseTime(index: Int, time: LocalTime) = edit {
        copy(
            doseTimes = doseTimes.toMutableList()
                .also { it[index] = it[index].copy(time = time) }
                .sortedBy { it.time },
        )
    }

    fun updateDoseAmount(index: Int, amount: Double) = edit {
        copy(
            doseTimes = doseTimes.toMutableList()
                .also { it[index] = it[index].copy(amount = amount.coerceAtLeast(0.0)) },
        )
    }

    fun removeDoseTime(index: Int) = edit {
        copy(doseTimes = doseTimes.filterIndexed { i, _ -> i != index })
    }

    // --- stock -------------------------------------------------------------

    fun setStockTracking(enabled: Boolean) = edit { copy(stock = stock.copy(trackingEnabled = enabled)) }
    fun setStockCount(value: Double) = edit { copy(stock = stock.copy(count = value.coerceAtLeast(0.0))) }
    fun setUnitsPerPackage(value: Double) = edit { copy(stock = stock.copy(unitsPerPackage = value.coerceAtLeast(0.0))) }
    fun setLeadTimeDays(value: Int) = edit { copy(stock = stock.copy(leadTimeDays = value.coerceIn(0, 365))) }
    fun setExpiryDate(date: LocalDate?) = edit { copy(stock = stock.copy(expiryDate = date)) }
    fun setBufferDays(value: Int) = edit { copy(stock = stock.copy(bufferDays = value.coerceIn(0, 365))) }

    // --- barcodes ----------------------------------------------------------

    fun addBarcode(code: String) = edit { copy(barcodes = (barcodes + code).distinct()) }

    fun removeBarcode(code: String) = edit { copy(barcodes = barcodes - code) }

    /**
     * Which other medicine currently owns a code, so the form can ask before taking it away.
     * The unique index on the barcode table means saving would move it either way.
     */
    suspend fun ownerOf(code: String): Medicine? =
        container.medicineRepository.findByBarcode(code)?.takeIf { it.id != medicineId }

    // --- reminders ---------------------------------------------------------

    fun setRemindersEnabled(value: Boolean) = edit { copy(reminders = reminders.copy(enabled = value)) }
    fun setSnoozeMinutes(value: Int) = edit { copy(reminders = reminders.copy(snoozeMinutes = value.coerceIn(1, 720))) }
    fun setRepeatIfIgnored(value: Boolean) = edit { copy(reminders = reminders.copy(repeatIfIgnored = value)) }
    fun setSoundUri(uri: String?) = edit { copy(reminders = reminders.copy(soundUri = uri)) }
    fun setVibrate(value: Boolean) = edit { copy(reminders = reminders.copy(vibrate = value)) }

    fun save() {
        val current = _state.value
        if (!current.canSave) return
        viewModelScope.launch {
            val draft = current.draft.copy(name = current.draft.name.trim())

            // Sound and vibration live on the notification channel, and Android will not let an
            // existing channel change either one. Bumping the version makes the app register a
            // fresh channel; without this the new sound is saved but never actually heard.
            val previous = original?.reminders
            val channelChanged = previous != null &&
                (previous.soundUri != draft.reminders.soundUri || previous.vibrate != draft.reminders.vibrate)

            val toSave = if (channelChanged) {
                draft.copy(reminders = draft.reminders.copy(channelVersion = previous!!.channelVersion + 1))
            } else {
                draft
            }

            val id = container.medicineRepository.save(toSave)
            container.reminderCoordinator.onMedicineSaved(id)
            _state.value = _state.value.copy(saved = true)
        }
    }

    companion object {
        private fun emptyDraft() = Medicine(
            name = "",
            startDate = LocalDate.now(),
            schedule = Schedule(cycleAnchorDate = LocalDate.now()),
            doseTimes = listOf(DoseTime(time = LocalTime.of(8, 0))),
        )

        /**
         * A scan fills in what it could read: the code itself always, plus a name and expiry date
         * when the box carried them. Stock tracking is switched on for a new medicine because
         * scanning a package is a statement that the count matters.
         */
        private fun Medicine.withPrefill(prefill: ScanPrefill?): Medicine {
            if (prefill == null || prefill.isEmpty) return this
            return copy(
                name = prefill.name?.takeIf { name.isBlank() } ?: name,
                dosage = prefill.dosage?.takeIf { dosage.isBlank() } ?: dosage,
                barcodes = (barcodes + listOfNotNull(prefill.gtin)).distinct(),
                stock = stock.copy(
                    trackingEnabled = stock.trackingEnabled || id == 0L,
                    expiryDate = ExpiryWatcher.merge(stock.expiryDate, prefill.expiry),
                ),
            )
        }

        fun factory(
            container: AppContainer,
            medicineId: Long,
            prefill: ScanPrefill? = null,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { MedicineEditViewModel(container, medicineId, prefill) }
            }
    }
}
