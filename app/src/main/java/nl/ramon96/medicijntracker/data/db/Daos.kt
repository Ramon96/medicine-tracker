package nl.ramon96.medicijntracker.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicineDao {

    @Transaction
    @Query("SELECT * FROM medicine ORDER BY active DESC, name COLLATE NOCASE")
    fun observeAll(): Flow<List<MedicineWithDoseTimes>>

    @Transaction
    @Query("SELECT * FROM medicine ORDER BY active DESC, name COLLATE NOCASE")
    suspend fun getAll(): List<MedicineWithDoseTimes>

    @Transaction
    @Query("SELECT * FROM medicine WHERE active = 1")
    suspend fun getActive(): List<MedicineWithDoseTimes>

    @Transaction
    @Query("SELECT * FROM medicine WHERE id = :id")
    fun observeById(id: Long): Flow<MedicineWithDoseTimes?>

    @Transaction
    @Query("SELECT * FROM medicine WHERE id = :id")
    suspend fun getById(id: Long): MedicineWithDoseTimes?

    @Upsert
    suspend fun upsert(medicine: MedicineEntity): Long

    @Delete
    suspend fun delete(medicine: MedicineEntity)

    @Query("DELETE FROM dose_time WHERE medicineId = :medicineId")
    suspend fun deleteDoseTimesFor(medicineId: Long)

    @Insert
    suspend fun insertDoseTimes(doseTimes: List<DoseTimeEntity>)

    /**
     * Saves the medicine and replaces its dose times in one transaction, so a screen never sees
     * a medicine with half its schedule.
     */
    @Transaction
    suspend fun save(medicine: MedicineEntity, doseTimes: List<DoseTimeEntity>): Long {
        val id = upsert(medicine)
        val medicineId = if (medicine.id == 0L) id else medicine.id
        deleteDoseTimesFor(medicineId)
        insertDoseTimes(doseTimes.map { it.copy(id = 0, medicineId = medicineId) })
        return medicineId
    }

    @Query("UPDATE medicine SET stockCount = MAX(0, stockCount + :delta) WHERE id = :medicineId")
    suspend fun adjustStock(medicineId: Long, delta: Double)

    @Query("UPDATE medicine SET lastAlertDate = :isoDate WHERE id = :medicineId")
    suspend fun markRefillAlerted(medicineId: Long, isoDate: String)

    @Query("UPDATE medicine SET channelVersion = channelVersion + 1, soundUri = :soundUri WHERE id = :medicineId")
    suspend fun updateSound(medicineId: Long, soundUri: String?)
}

@Dao
interface IntakeDao {

    /** Ignores rows that already exist, which is what makes re-arming a day idempotent. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoring(intakes: List<IntakeEntity>): List<Long>

    @Update
    suspend fun update(intake: IntakeEntity)

    @Query("SELECT * FROM intake WHERE id = :id")
    suspend fun getById(id: Long): IntakeEntity?

    @Query("SELECT * FROM intake WHERE medicineId = :medicineId AND scheduledAt = :scheduledAt")
    suspend fun getByMoment(medicineId: Long, scheduledAt: String): IntakeEntity?

    @Query(
        """
        SELECT * FROM intake
        WHERE scheduledAt >= :fromInclusive AND scheduledAt < :toExclusive
        ORDER BY scheduledAt
        """,
    )
    fun observeBetween(fromInclusive: String, toExclusive: String): Flow<List<IntakeEntity>>

    @Query(
        """
        SELECT * FROM intake
        WHERE scheduledAt >= :fromInclusive AND scheduledAt < :toExclusive
        ORDER BY scheduledAt
        """,
    )
    suspend fun getBetween(fromInclusive: String, toExclusive: String): List<IntakeEntity>

    /**
     * Doses still waiting for an answer whose reminder moment falls in the window. A snoozed
     * dose is armed for its snooze moment, which is why both columns are considered.
     */
    @Query(
        """
        SELECT * FROM intake
        WHERE status IN ('PENDING', 'SNOOZED')
          AND COALESCE(snoozedUntil, scheduledAt) >= :fromInclusive
          AND COALESCE(snoozedUntil, scheduledAt) < :toExclusive
        ORDER BY COALESCE(snoozedUntil, scheduledAt)
        """,
    )
    suspend fun getOpenBetween(fromInclusive: String, toExclusive: String): List<IntakeEntity>

    @Query(
        """
        UPDATE intake SET status = 'MISSED'
        WHERE status IN ('PENDING', 'SNOOZED')
          AND COALESCE(snoozedUntil, scheduledAt) < :beforeMoment
        """,
    )
    suspend fun markOverdueAsMissed(beforeMoment: String): Int

    @Query("SELECT COUNT(*) FROM intake WHERE medicineId = :medicineId AND status = :status AND scheduledAt >= :fromInclusive")
    suspend fun countByStatus(medicineId: Long, status: String, fromInclusive: String): Int

    @Query("DELETE FROM intake WHERE medicineId = :medicineId AND status = 'PENDING' AND scheduledAt >= :fromMoment")
    suspend fun deleteFuturePending(medicineId: Long, fromMoment: String)
}
