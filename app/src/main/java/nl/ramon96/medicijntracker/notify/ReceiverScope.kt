package nl.ramon96.medicijntracker.notify

import android.content.BroadcastReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * Runs suspending work from a broadcast receiver.
 *
 * `goAsync()` keeps the process alive past `onReceive`, which matters here: database work and
 * re-arming alarms must finish even though the receiver itself returns immediately.
 */
internal fun goAsyncInScope(receiver: BroadcastReceiver, block: suspend () -> Unit) {
    val pendingResult = receiver.goAsync()
    receiverScope.launch {
        try {
            block()
        } finally {
            pendingResult.finish()
        }
    }
}
