package nl.ramon96.medicijntracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MedicineEntity::class, DoseTimeEntity::class, IntakeEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class MedicijnDatabase : RoomDatabase() {

    abstract fun medicineDao(): MedicineDao
    abstract fun intakeDao(): IntakeDao

    companion object {
        @Volatile
        private var instance: MedicijnDatabase? = null

        fun get(context: Context): MedicijnDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MedicijnDatabase::class.java,
                    "medicijnen.db",
                )
                    // Foreign keys drive the cascade delete of dose times and intakes.
                    .build()
                    .also { instance = it }
            }
    }
}
