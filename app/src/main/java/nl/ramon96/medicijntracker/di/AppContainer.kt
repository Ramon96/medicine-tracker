package nl.ramon96.medicijntracker.di

import android.content.Context
import nl.ramon96.medicijntracker.data.db.MedicijnDatabase
import nl.ramon96.medicijntracker.data.prefs.AppSettings
import nl.ramon96.medicijntracker.data.repo.DoseRepository
import nl.ramon96.medicijntracker.data.repo.MedicineRepository
import nl.ramon96.medicijntracker.notify.DoseAlarmScheduler
import nl.ramon96.medicijntracker.notify.ReminderCoordinator

/**
 * Hand-rolled dependency container.
 *
 * The graph is small and entirely singleton-scoped, so a DI framework would add a compiler
 * plugin and a build step without buying anything here. Broadcast receivers, workers, the widget
 * and the view models all reach the same instances through [nl.ramon96.medicijntracker.MedicijnApp].
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val database: MedicijnDatabase by lazy { MedicijnDatabase.get(appContext) }

    val settings: AppSettings by lazy { AppSettings(appContext) }

    val medicineRepository: MedicineRepository by lazy { MedicineRepository(database.medicineDao()) }

    val doseRepository: DoseRepository by lazy {
        DoseRepository(database.intakeDao(), medicineRepository)
    }

    val alarmScheduler: DoseAlarmScheduler by lazy { DoseAlarmScheduler(appContext) }

    val reminderCoordinator: ReminderCoordinator by lazy {
        ReminderCoordinator(
            context = appContext,
            medicineRepository = medicineRepository,
            doseRepository = doseRepository,
            alarmScheduler = alarmScheduler,
            settings = settings,
        )
    }
}
