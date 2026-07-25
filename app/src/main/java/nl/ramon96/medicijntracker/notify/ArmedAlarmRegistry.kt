package nl.ramon96.medicijntracker.notify

import android.content.Context

/**
 * Remembers which doses currently have an alarm.
 *
 * Android cannot enumerate pending alarms, so without this list a dose that was armed and then
 * taken, edited or deleted would keep its alarm and fire a reminder for something that no longer
 * exists. Plain SharedPreferences because it is read from broadcast receivers on the main thread.
 */
internal class ArmedAlarmRegistry(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("armed_alarms", Context.MODE_PRIVATE)

    fun armedIds(): Set<Long> =
        prefs.getStringSet(KEY_IDS, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()

    fun replace(ids: Set<Long>) {
        prefs.edit().putStringSet(KEY_IDS, ids.map(Long::toString).toSet()).apply()
    }

    fun add(id: Long) = replace(armedIds() + id)

    fun remove(id: Long) = replace(armedIds() - id)

    private companion object {
        const val KEY_IDS = "ids"
    }
}
