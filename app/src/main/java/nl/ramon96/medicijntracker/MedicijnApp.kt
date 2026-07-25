package nl.ramon96.medicijntracker

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import nl.ramon96.medicijntracker.di.AppContainer
import nl.ramon96.medicijntracker.notify.DailyMaintenanceWorker
import nl.ramon96.medicijntracker.notify.NotificationChannels
import nl.ramon96.medicijntracker.update.UpdateCheckWorker

class MedicijnApp : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        NotificationChannels.ensureRefillChannel(this)
        DailyMaintenanceWorker.schedule(this)
        UpdateCheckWorker.schedule(this)

        // Catch up on anything the phone missed while the app was not running.
        appScope.launch { container.reminderCoordinator.refreshAll() }
    }

    companion object {
        fun containerOf(context: Context): AppContainer =
            (context.applicationContext as MedicijnApp).container
    }
}
