package nl.ramon96.medicijntracker.data.db

import nl.ramon96.medicijntracker.domain.model.DoseTime
import nl.ramon96.medicijntracker.domain.model.Intake
import nl.ramon96.medicijntracker.domain.model.IntakeStatus
import nl.ramon96.medicijntracker.domain.model.Medicine
import nl.ramon96.medicijntracker.domain.model.MedicineForm
import nl.ramon96.medicijntracker.domain.model.ReminderSettings
import nl.ramon96.medicijntracker.domain.model.Schedule
import nl.ramon96.medicijntracker.domain.model.ScheduleType
import nl.ramon96.medicijntracker.domain.model.StockInfo
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Dates are stored as ISO text rather than numbers: it sorts and range-filters correctly in SQL
 * and stays readable when inspecting the database.
 */
private val dateTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

fun LocalDateTime.toDb(): String = format(dateTimeFormat)
fun String.toLocalDateTime(): LocalDateTime = LocalDateTime.parse(this, dateTimeFormat)
fun LocalDate.toDb(): String = toString()
fun String.toLocalDate(): LocalDate = LocalDate.parse(this)

private inline fun <reified T : Enum<T>> String.toEnumOr(fallback: T): T =
    runCatching { enumValueOf<T>(this) }.getOrDefault(fallback)

fun MedicineWithDoseTimes.toDomain(): Medicine = Medicine(
    id = medicine.id,
    name = medicine.name,
    dosage = medicine.dosage,
    form = medicine.form.toEnumOr(MedicineForm.OTHER),
    notes = medicine.notes,
    colorArgb = medicine.colorArgb,
    active = medicine.active,
    startDate = medicine.startDate.toLocalDate(),
    endDate = medicine.endDate?.toLocalDate(),
    schedule = Schedule(
        type = medicine.scheduleType.toEnumOr(ScheduleType.DAILY),
        intervalDays = medicine.intervalDays,
        weekdayMask = medicine.weekdayMask,
        cycleActiveDays = medicine.cycleActiveDays,
        cyclePauseDays = medicine.cyclePauseDays,
        cycleAnchorDate = medicine.cycleAnchorDate?.toLocalDate(),
        remindOnPauseDays = medicine.remindOnPauseDays,
    ),
    stock = StockInfo(
        count = medicine.stockCount,
        unitsPerPackage = medicine.unitsPerPackage,
        leadTimeDays = medicine.leadTimeDays,
        bufferDays = medicine.bufferDays,
        trackingEnabled = medicine.stockTrackingEnabled,
        lastAlertDate = medicine.lastAlertDate?.toLocalDate(),
        expiryDate = medicine.expiryDate?.toLocalDate(),
    ),
    reminders = ReminderSettings(
        enabled = medicine.remindersEnabled,
        snoozeMinutes = medicine.snoozeMinutes,
        repeatIfIgnored = medicine.repeatIfIgnored,
        soundUri = medicine.soundUri,
        channelVersion = medicine.channelVersion,
        vibrate = medicine.vibrate,
    ),
    doseTimes = doseTimes
        .sortedBy { it.minuteOfDay }
        .map { DoseTime(id = it.id, time = LocalTime.ofSecondOfDay(it.minuteOfDay * 60L), amount = it.amount) },
    barcodes = barcodes.map { it.code },
)

fun Medicine.toEntity(): MedicineEntity = MedicineEntity(
    id = id,
    name = name,
    dosage = dosage,
    form = form.name,
    notes = notes,
    colorArgb = colorArgb,
    active = active,
    startDate = startDate.toDb(),
    endDate = endDate?.toDb(),
    scheduleType = schedule.type.name,
    intervalDays = schedule.intervalDays,
    weekdayMask = schedule.weekdayMask,
    cycleActiveDays = schedule.cycleActiveDays,
    cyclePauseDays = schedule.cyclePauseDays,
    cycleAnchorDate = schedule.cycleAnchorDate?.toDb(),
    remindOnPauseDays = schedule.remindOnPauseDays,
    stockCount = stock.count,
    unitsPerPackage = stock.unitsPerPackage,
    leadTimeDays = stock.leadTimeDays,
    bufferDays = stock.bufferDays,
    stockTrackingEnabled = stock.trackingEnabled,
    lastAlertDate = stock.lastAlertDate?.toDb(),
    expiryDate = stock.expiryDate?.toDb(),
    remindersEnabled = reminders.enabled,
    snoozeMinutes = reminders.snoozeMinutes,
    repeatIfIgnored = reminders.repeatIfIgnored,
    soundUri = reminders.soundUri,
    channelVersion = reminders.channelVersion,
    vibrate = reminders.vibrate,
)

fun Medicine.toDoseTimeEntities(): List<DoseTimeEntity> = doseTimes.map {
    DoseTimeEntity(
        id = it.id,
        medicineId = id,
        minuteOfDay = it.time.hour * 60 + it.time.minute,
        amount = it.amount,
    )
}

fun Medicine.toBarcodeEntities(): List<MedicineBarcodeEntity> =
    barcodes.distinct().map { MedicineBarcodeEntity(medicineId = id, code = it) }

fun IntakeEntity.toDomain(): Intake = Intake(
    id = id,
    medicineId = medicineId,
    scheduledAt = scheduledAt.toLocalDateTime(),
    actualAt = actualAt?.toLocalDateTime(),
    snoozedUntil = snoozedUntil?.toLocalDateTime(),
    status = status.toEnumOr(IntakeStatus.PENDING),
    amount = amount,
)

fun Intake.toEntity(): IntakeEntity = IntakeEntity(
    id = id,
    medicineId = medicineId,
    scheduledAt = scheduledAt.toDb(),
    actualAt = actualAt?.toDb(),
    snoozedUntil = snoozedUntil?.toDb(),
    status = status.name,
    amount = amount,
)
