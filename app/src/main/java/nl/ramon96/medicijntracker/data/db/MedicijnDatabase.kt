package nl.ramon96.medicijntracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Database(
    entities = [MedicineEntity::class, DoseTimeEntity::class, IntakeEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class MedicijnDatabase : RoomDatabase() {

    abstract fun medicineDao(): MedicineDao
    abstract fun intakeDao(): IntakeDao

    companion object {
        /** Adds the per-medicine vibration switch that came with the custom-sound settings. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE medicine ADD COLUMN vibrate INTEGER NOT NULL DEFAULT 1",
                )
            }
        }

        @Volatile
        private var instance: MedicijnDatabase? = null

        fun get(context: Context): MedicijnDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MedicijnDatabase::class.java,
                    "medicijnen.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    // Foreign keys drive the cascade delete of dose times and intakes.
                    .build()
                    .also { instance = it }
            }
    }
}
