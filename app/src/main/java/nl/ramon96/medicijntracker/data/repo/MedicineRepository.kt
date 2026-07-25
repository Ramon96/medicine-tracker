package nl.ramon96.medicijntracker.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import nl.ramon96.medicijntracker.data.db.MedicineDao
import nl.ramon96.medicijntracker.data.db.toDb
import nl.ramon96.medicijntracker.data.db.toDoseTimeEntities
import nl.ramon96.medicijntracker.data.db.toDomain
import nl.ramon96.medicijntracker.data.db.toEntity
import nl.ramon96.medicijntracker.domain.model.Medicine
import java.time.LocalDate

class MedicineRepository(private val medicineDao: MedicineDao) {

    fun observeAll(): Flow<List<Medicine>> =
        medicineDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeById(id: Long): Flow<Medicine?> =
        medicineDao.observeById(id).map { it?.toDomain() }

    suspend fun getById(id: Long): Medicine? = medicineDao.getById(id)?.toDomain()

    suspend fun getAll(): List<Medicine> = medicineDao.getAll().map { it.toDomain() }

    suspend fun getActive(): List<Medicine> = medicineDao.getActive().map { it.toDomain() }

    /** Inserts or updates the medicine together with its dose times; returns its id. */
    suspend fun save(medicine: Medicine): Long =
        medicineDao.save(medicine.toEntity(), medicine.toDoseTimeEntities())

    suspend fun delete(medicine: Medicine) = medicineDao.delete(medicine.toEntity())

    /** Positive to add stock (new package), negative when a dose is taken. */
    suspend fun adjustStock(medicineId: Long, delta: Double) =
        medicineDao.adjustStock(medicineId, delta)

    suspend fun markRefillAlerted(medicineId: Long, date: LocalDate) =
        medicineDao.markRefillAlerted(medicineId, date.toDb())

    /**
     * Changing a notification sound also bumps the channel version: notification channels are
     * immutable once created, so the app has to register a new one for the sound to take effect.
     */
    suspend fun updateSound(medicineId: Long, soundUri: String?) =
        medicineDao.updateSound(medicineId, soundUri)
}
