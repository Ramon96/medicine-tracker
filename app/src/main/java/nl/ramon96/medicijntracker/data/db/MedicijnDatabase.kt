package nl.ramon96.medicijntracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Database(
    entities = [
        MedicineEntity::class,
        DoseTimeEntity::class,
        IntakeEntity::class,
        MedicineBarcodeEntity::class,
    ],
    version = 3,
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

        /**
         * Barcode scanning: the codes found on a package, and the expiry date read off it.
         *
         * The statements below have to match what Room generates for the entities byte for byte,
         * or the app refuses to open the database on the first launch after an update - which
         * means someone loses access to their medication list. They were taken from the generated
         * `app/schemas/.../3.json`; if the entities change, regenerate and copy them again rather
         * than editing by hand.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE medicine ADD COLUMN expiryDate TEXT")
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `medicine_barcode` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`medicineId` INTEGER NOT NULL, " +
                        "`code` TEXT NOT NULL, " +
                        "FOREIGN KEY(`medicineId`) REFERENCES `medicine`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_medicine_barcode_medicineId` " +
                        "ON `medicine_barcode` (`medicineId`)",
                )
                connection.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_medicine_barcode_code` " +
                        "ON `medicine_barcode` (`code`)",
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    // Foreign keys drive the cascade delete of dose times and intakes.
                    .build()
                    .also { instance = it }
            }
    }
}
