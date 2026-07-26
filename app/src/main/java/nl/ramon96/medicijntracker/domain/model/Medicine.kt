package nl.ramon96.medicijntracker.domain.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** How a medicine is taken. Only used for wording and icons in the UI. */
enum class MedicineForm { TABLET, CAPSULE, DROPS, INJECTION, SPRAY, SACHET, OTHER }

/** A single moment on a dosing day, e.g. 08:00 - 1 tablet. */
data class DoseTime(
    val id: Long = 0,
    val time: LocalTime,
    val amount: Double = 1.0,
)

/**
 * A medicine as the user defined it. Nothing here is hard-coded: every field comes from the
 * add/edit form, so any regimen (pill, antidepressant, vitamin, ...) is expressed with the
 * same model.
 */
data class Medicine(
    val id: Long = 0,
    val name: String,
    val dosage: String = "",
    val form: MedicineForm = MedicineForm.TABLET,
    val notes: String = "",
    val colorArgb: Int = DEFAULT_COLOR,
    val active: Boolean = true,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val schedule: Schedule = Schedule(),
    val stock: StockInfo = StockInfo(),
    val reminders: ReminderSettings = ReminderSettings(),
    val doseTimes: List<DoseTime> = emptyList(),
    /**
     * Product numbers of the packages this medicine comes in, canonical GTIN-14, as produced by
     * [nl.ramon96.medicijntracker.domain.barcode.Gs1Parser].
     *
     * A list rather than a single code: the pharmacy hands out different pack sizes and switches
     * between generic manufacturers, and each of those is its own product number for the same
     * medicine. Scanning any of them has to recognise it.
     */
    val barcodes: List<String> = emptyList(),
) {
    /** Total units taken on a day this medicine is due. */
    val unitsPerDosingDay: Double get() = doseTimes.sumOf { it.amount }

    companion object {
        const val DEFAULT_COLOR: Int = 0xFF4C7DF0.toInt()
    }
}

/** One occurrence of a dose, produced by [nl.ramon96.medicijntracker.domain.schedule.ScheduleCalculator]. */
data class PlannedDose(
    val medicineId: Long,
    val scheduledAt: LocalDateTime,
    val amount: Double,
    /** True on the pause days of a cycle schedule (placebo pills / stop week). */
    val isPauseDay: Boolean = false,
)
