package nl.ramon96.medicijntracker.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "medicine")
data class MedicineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dosage: String = "",
    val form: String = "TABLET",
    val notes: String = "",
    val colorArgb: Int = 0,
    val active: Boolean = true,
    /** ISO date, e.g. 2026-01-31. */
    val startDate: String,
    val endDate: String? = null,

    val scheduleType: String = "DAILY",
    val intervalDays: Int = 2,
    val weekdayMask: Int = 0b111_1111,
    val cycleActiveDays: Int = 21,
    val cyclePauseDays: Int = 7,
    val cycleAnchorDate: String? = null,
    val remindOnPauseDays: Boolean = false,

    val stockCount: Double = 0.0,
    val unitsPerPackage: Double = 0.0,
    val leadTimeDays: Int = 0,
    val bufferDays: Int = 3,
    val stockTrackingEnabled: Boolean = false,
    val lastAlertDate: String? = null,
    /** ISO date; expiry of the oldest package, from a scanned GS1 code or typed in. */
    val expiryDate: String? = null,

    val remindersEnabled: Boolean = true,
    val snoozeMinutes: Int = 15,
    val repeatIfIgnored: Boolean = true,
    val soundUri: String? = null,
    val channelVersion: Int = 1,
    val vibrate: Boolean = true,
)

@Entity(
    tableName = "dose_time",
    foreignKeys = [
        ForeignKey(
            entity = MedicineEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicineId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("medicineId")],
)
data class DoseTimeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicineId: Long,
    /** Minutes since midnight; keeps ordering and comparison trivial in SQL. */
    val minuteOfDay: Int,
    val amount: Double = 1.0,
)

@Entity(
    tableName = "intake",
    foreignKeys = [
        ForeignKey(
            entity = MedicineEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicineId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        // Unique so re-generating a day's doses (after a reboot, or twice on the same day)
        // can never produce duplicate rows or duplicate notifications.
        Index(value = ["medicineId", "scheduledAt"], unique = true),
        Index("scheduledAt"),
    ],
)
data class IntakeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicineId: Long,
    /** ISO local date-time, e.g. 2026-01-31T08:00:00 - sortable as plain text. */
    val scheduledAt: String,
    val actualAt: String? = null,
    /** When a snoozed dose should fire again; the reminder is re-armed from this column. */
    val snoozedUntil: String? = null,
    val status: String = "PENDING",
    val amount: Double = 1.0,
)

/**
 * A product number found on a package of this medicine.
 *
 * Its own table rather than a column on [MedicineEntity] for two reasons. One medicine genuinely
 * has several codes - pack sizes differ and the pharmacy switches generic manufacturers - so a
 * single column would mean "last scan wins" and would quietly stop recognising the box scanned
 * last month. And the query that matters runs the other way round ("which medicine owns this
 * code?"), which wants the unique index below rather than a scan over every medicine.
 */
@Entity(
    tableName = "medicine_barcode",
    foreignKeys = [
        ForeignKey(
            entity = MedicineEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicineId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("medicineId"),
        // A physical package identifies exactly one medicine, and the scan flow looks codes up
        // by this column on every scan.
        Index(value = ["code"], unique = true),
    ],
)
data class MedicineBarcodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicineId: Long,
    /** Canonical GTIN-14, so an EAN-13 and a DataMatrix of the same pack collapse to one key. */
    val code: String,
)

/** A medicine together with its dose times, the shape every screen needs. */
data class MedicineWithDoseTimes(
    @Embedded val medicine: MedicineEntity,
    @Relation(parentColumn = "id", entityColumn = "medicineId")
    val doseTimes: List<DoseTimeEntity>,
    @Relation(parentColumn = "id", entityColumn = "medicineId")
    val barcodes: List<MedicineBarcodeEntity> = emptyList(),
)
