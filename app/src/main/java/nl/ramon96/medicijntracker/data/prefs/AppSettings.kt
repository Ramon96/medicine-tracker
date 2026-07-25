package nl.ramon96.medicijntracker.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "instellingen")

/** The handful of app-wide preferences; everything else lives per medicine. */
class AppSettings(private val context: Context) {

    private object Keys {
        val batteryHintDismissed = booleanPreferencesKey("battery_hint_dismissed")
        val defaultSnoozeMinutes = intPreferencesKey("default_snooze_minutes")
        val refillThrottleDays = intPreferencesKey("refill_throttle_days")
    }

    val batteryHintDismissed: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.batteryHintDismissed] ?: false }

    val defaultSnoozeMinutes: Flow<Int> =
        context.dataStore.data.map { it[Keys.defaultSnoozeMinutes] ?: DEFAULT_SNOOZE_MINUTES }

    val refillThrottleDays: Flow<Int> =
        context.dataStore.data.map { it[Keys.refillThrottleDays] ?: DEFAULT_REFILL_THROTTLE_DAYS }

    suspend fun setBatteryHintDismissed(dismissed: Boolean) {
        context.dataStore.edit { it[Keys.batteryHintDismissed] = dismissed }
    }

    suspend fun setDefaultSnoozeMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.defaultSnoozeMinutes] = minutes.coerceIn(1, 24 * 60) }
    }

    suspend fun setRefillThrottleDays(days: Int) {
        context.dataStore.edit { it[Keys.refillThrottleDays] = days.coerceIn(1, 30) }
    }

    suspend fun currentRefillThrottleDays(): Int = refillThrottleDays.first()

    companion object {
        const val DEFAULT_SNOOZE_MINUTES = 15
        const val DEFAULT_REFILL_THROTTLE_DAYS = 3
    }
}
