package nl.ramon96.medicijntracker.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "instellingen")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class AppFont { SANS, SERIF, MONO }

/** Everything that decides how the app looks, read as one object by the theme. */
data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val paletteId: String = "roze",
    /** Follow the wallpaper colours (Material You) instead of the chosen palette. */
    val dynamicColor: Boolean = false,
    val highContrast: Boolean = false,
    /** Multiplier on every text size in the app; 1.0 is the phone's own setting. */
    val textScale: Float = 1.0f,
    val font: AppFont = AppFont.SANS,
)

/** The handful of app-wide preferences; everything else lives per medicine. */
class AppSettings(private val context: Context) {

    private object Keys {
        val batteryHintDismissed = booleanPreferencesKey("battery_hint_dismissed")
        val defaultSnoozeMinutes = intPreferencesKey("default_snooze_minutes")
        val refillThrottleDays = intPreferencesKey("refill_throttle_days")

        val themeMode = stringPreferencesKey("theme_mode")
        val palette = stringPreferencesKey("palette")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val highContrast = booleanPreferencesKey("high_contrast")
        val textScale = floatPreferencesKey("text_scale")
        val font = stringPreferencesKey("font")

        val autoUpdateCheck = booleanPreferencesKey("auto_update_check")
    }

    private inline fun <reified T : Enum<T>> String?.toEnumOr(fallback: T): T =
        this?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

    val batteryHintDismissed: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.batteryHintDismissed] ?: false }

    val defaultSnoozeMinutes: Flow<Int> =
        context.dataStore.data.map { it[Keys.defaultSnoozeMinutes] ?: DEFAULT_SNOOZE_MINUTES }

    val refillThrottleDays: Flow<Int> =
        context.dataStore.data.map { it[Keys.refillThrottleDays] ?: DEFAULT_REFILL_THROTTLE_DAYS }

    val autoUpdateCheck: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.autoUpdateCheck] ?: true }

    /** Read as a single object so the theme recomposes once per change, not six times. */
    val theme: Flow<ThemeSettings> = context.dataStore.data.map { prefs ->
        ThemeSettings(
            mode = prefs[Keys.themeMode].toEnumOr(ThemeMode.SYSTEM),
            paletteId = prefs[Keys.palette] ?: ThemeSettings().paletteId,
            dynamicColor = prefs[Keys.dynamicColor] ?: false,
            highContrast = prefs[Keys.highContrast] ?: false,
            textScale = (prefs[Keys.textScale] ?: 1.0f).coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE),
            font = prefs[Keys.font].toEnumOr(AppFont.SANS),
        )
    }

    suspend fun setBatteryHintDismissed(dismissed: Boolean) {
        context.dataStore.edit { it[Keys.batteryHintDismissed] = dismissed }
    }

    suspend fun setDefaultSnoozeMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.defaultSnoozeMinutes] = minutes.coerceIn(1, 24 * 60) }
    }

    suspend fun setRefillThrottleDays(days: Int) {
        context.dataStore.edit { it[Keys.refillThrottleDays] = days.coerceIn(1, 30) }
    }

    suspend fun setAutoUpdateCheck(enabled: Boolean) {
        context.dataStore.edit { it[Keys.autoUpdateCheck] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.themeMode] = mode.name }
    }

    suspend fun setPalette(paletteId: String) {
        context.dataStore.edit { it[Keys.palette] = paletteId }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.dynamicColor] = enabled }
    }

    suspend fun setHighContrast(enabled: Boolean) {
        context.dataStore.edit { it[Keys.highContrast] = enabled }
    }

    suspend fun setTextScale(scale: Float) {
        context.dataStore.edit { it[Keys.textScale] = scale.coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE) }
    }

    suspend fun setFont(font: AppFont) {
        context.dataStore.edit { it[Keys.font] = font.name }
    }

    suspend fun currentRefillThrottleDays(): Int = refillThrottleDays.first()

    suspend fun currentTheme(): ThemeSettings = theme.first()

    companion object {
        const val DEFAULT_SNOOZE_MINUTES = 15
        const val DEFAULT_REFILL_THROTTLE_DAYS = 3

        const val MIN_TEXT_SCALE = 0.85f
        const val MAX_TEXT_SCALE = 1.6f
    }
}
