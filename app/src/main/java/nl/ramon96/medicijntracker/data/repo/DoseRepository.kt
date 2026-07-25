package nl.ramon96.medicijntracker.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import nl.ramon96.medicijntracker.data.db.IntakeDao
import nl.ramon96.medicijntracker.data.db.IntakeEntity
import nl.ramon96.medicijntracker.data.db.toDb
import nl.ramon96.medicijntracker.data.db.toDomain
import nl.ramon96.medicijntracker.domain.model.Intake
import nl.ramon96.medicijntracker.domain.model.IntakeStatus
import nl.ramon96.medicijntracker.domain.model.IntakeWithMedicine
import nl.ramon96.medicijntracker.domain.model.Medicine
import nl.ramon96.medicijntracker.domain.schedule.ScheduleCalculator
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Turns schedules into concrete dose rows and records what happened to them.
 *
 * Rows are generated ahead of time rather than derived on the fly so a dose keeps its history
 * even if the medicine's schedule is edited afterwards.
 */
class DoseRepository(
    private val intakeDao: IntakeDao,
    private val medicineRepository: MedicineRepository,
) {

    /**
     * Makes sure every active medicine has rows for [date]. Safe to call repeatedly - the unique
     * index on (medicineId, scheduledAt) drops anything that already exists.
     */
    suspend fun generateDosesFor(date: LocalDate, medicines: List<Medicine>? = null): Int {
        val active = medicines ?: medicineRepository.getActive()
        val rows = active.flatMap { medicine ->
            ScheduleCalculator.dosesOn(medicine, date).map { dose ->
                IntakeEntity(
                    medicineId = dose.medicineId,
                    scheduledAt = dose.scheduledAt.toDb(),
                    status = IntakeStatus.PENDING.name,
                    amount = dose.amount,
                )
            }
        }
        if (rows.isEmpty()) return 0
        return intakeDao.insertIgnoring(rows).count { it != -1L }
    }

    /** Generates today plus the next [daysAhead] days, used at startup and by the daily worker. */
    suspend fun generateUpcoming(from: LocalDate, daysAhead: Int = 2) {
        val medicines = medicineRepository.getActive()
        (0..daysAhead).forEach { generateDosesFor(from.plusDays(it.toLong()), medicines) }
    }

    fun observeDay(date: LocalDate): Flow<List<IntakeWithMedicine>> = observeRange(date, date)

    fun observeRange(fromInclusive: LocalDate, toInclusive: LocalDate): Flow<List<IntakeWithMedicine>> =
        combine(
            intakeDao.observeBetween(
                fromInclusive.atStartOfDay().toDb(),
                toInclusive.plusDays(1).atStartOfDay().toDb(),
            ),
            medicineRepository.observeAll(),
        ) { intakes, medicines ->
            val byId = medicines.associateBy { it.id }
            intakes.mapNotNull { entity ->
                byId[entity.medicineId]?.let { IntakeWithMedicine(entity.toDomain(), it) }
            }
        }

    suspend fun getRange(fromInclusive: LocalDate, toInclusive: LocalDate): List<Intake> =
        intakeDao.getBetween(
            fromInclusive.atStartOfDay().toDb(),
            toInclusive.plusDays(1).atStartOfDay().toDb(),
        ).map { it.toDomain() }

    suspend fun getById(intakeId: Long): Intake? = intakeDao.getById(intakeId)?.toDomain()

    suspend fun findByMoment(medicineId: Long, scheduledAt: LocalDateTime): Intake? =
        intakeDao.getByMoment(medicineId, scheduledAt.toDb())?.toDomain()

    /**
     * Records a dose as taken and takes it out of the stock, so the refill forecast stays in
     * step with reality. Taking an already-taken dose again is a no-op.
     */
    suspend fun markTaken(intakeId: Long, at: LocalDateTime = LocalDateTime.now()) {
        val entity = intakeDao.getById(intakeId) ?: return
        if (entity.status == IntakeStatus.TAKEN.name) return
        intakeDao.update(
            entity.copy(status = IntakeStatus.TAKEN.name, actualAt = at.toDb(), snoozedUntil = null),
        )
        medicineRepository.adjustStock(entity.medicineId, -entity.amount)
    }

    /** Reverts a dose back to pending, putting the units back into stock. */
    suspend fun undo(intakeId: Long) {
        val entity = intakeDao.getById(intakeId) ?: return
        if (entity.status == IntakeStatus.TAKEN.name) {
            medicineRepository.adjustStock(entity.medicineId, entity.amount)
        }
        intakeDao.update(
            entity.copy(status = IntakeStatus.PENDING.name, actualAt = null, snoozedUntil = null),
        )
    }

    suspend fun markSkipped(intakeId: Long) {
        val entity = intakeDao.getById(intakeId) ?: return
        if (entity.status == IntakeStatus.TAKEN.name) {
            medicineRepository.adjustStock(entity.medicineId, entity.amount)
        }
        intakeDao.update(
            entity.copy(status = IntakeStatus.SKIPPED.name, actualAt = null, snoozedUntil = null),
        )
    }

    /** Postpones the reminder; the coordinator arms a new alarm for [until]. */
    suspend fun markSnoozed(intakeId: Long, until: LocalDateTime) {
        val entity = intakeDao.getById(intakeId) ?: return
        if (entity.status != IntakeStatus.PENDING.name && entity.status != IntakeStatus.SNOOZED.name) return
        intakeDao.update(entity.copy(status = IntakeStatus.SNOOZED.name, snoozedUntil = until.toDb()))
    }

    /** Open doses whose reminder moment falls inside the window, used to arm alarms. */
    suspend fun getOpenBetween(from: LocalDateTime, to: LocalDateTime): List<Intake> =
        intakeDao.getOpenBetween(from.toDb(), to.toDb()).map { it.toDomain() }

    /** Closes the books on doses that were never acted on. */
    suspend fun markOverdueAsMissed(before: LocalDateTime): Int =
        intakeDao.markOverdueAsMissed(before.toDb())

    /** Drops not-yet-due rows so an edited schedule takes effect immediately. */
    suspend fun clearFuturePending(medicineId: Long, from: LocalDateTime = LocalDateTime.now()) =
        intakeDao.deleteFuturePending(medicineId, from.toDb())

    suspend fun countByStatus(medicineId: Long, status: IntakeStatus, since: LocalDate): Int =
        intakeDao.countByStatus(medicineId, status.name, since.atStartOfDay().toDb())
}
