package nl.ramon96.medicijntracker.scan

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import nl.ramon96.medicijntracker.domain.barcode.Gs1Parser
import nl.ramon96.medicijntracker.domain.barcode.ScannedCode

/** Why a scan produced nothing, phrased so the UI can show it straight to the user. */
enum class ScanError { PLAY_SERVICES_MISSING, MODULE_UNAVAILABLE, CAMERA_UNAVAILABLE, UNKNOWN }

sealed interface ScanOutcome {
    data class Scanned(val code: ScannedCode) : ScanOutcome
    data object Cancelled : ScanOutcome
    data class Failed(val error: ScanError, val detail: String? = null) : ScanOutcome
}

/**
 * Opens Google's barcode scanner and hands back what it read.
 *
 * The scanner lives in Google Play services rather than in this APK, which is the reason the app
 * asks for no camera permission: Play services opens the camera in its own process, shows its own
 * viewfinder, and returns only the decoded string. The trade is a hard dependency on Play
 * services, so every failure below has to stay visible - a phone without it must be told to fill
 * the medicine in by hand, not silently handed a button that does nothing.
 */
@Composable
fun rememberCodeScanner(onResult: (ScanOutcome) -> Unit): () -> Unit {
    val context = LocalContext.current
    // The scan runs in another activity, so the callback can come back after this composable has
    // gone; keeping it up to date avoids firing a stale lambda.
    val currentOnResult by rememberUpdatedState(onResult)

    return remember(context) {
        {
            startScan(context) { currentOnResult(it) }
        }
    }
}

/**
 * Play services states that scanning genuinely cannot recover from.
 *
 * Deliberately not "anything that is not SUCCESS": that check compares the installed Play
 * services against the version this client was built against, so a working phone reports
 * SERVICE_VERSION_UPDATE_REQUIRED or SERVICE_UPDATING and would be told it has no Play services
 * at all. Those cases are left to the scanner itself, which either prompts or fails with a
 * specific reason.
 */
private val FATAL_AVAILABILITY = setOf(
    ConnectionResult.SERVICE_MISSING,
    ConnectionResult.SERVICE_INVALID,
    ConnectionResult.SERVICE_DISABLED,
)

/**
 * The scanner opens its own activity, so it needs an activity to start from. Compose only
 * promises a Context, and on some setups that is a wrapper rather than the activity itself -
 * which the library resolves to null and then dereferences.
 */
private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun startScan(context: Context, onResult: (ScanOutcome) -> Unit) {
    val availability = runCatching {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
    }.getOrDefault(ConnectionResult.SUCCESS)

    if (availability in FATAL_AVAILABILITY) {
        onResult(ScanOutcome.Failed(ScanError.PLAY_SERVICES_MISSING))
        return
    }

    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            // The GS1 code on EU prescription packs, which also carries the expiry date.
            Barcode.FORMAT_DATA_MATRIX,
            // The linear barcode on the same box, and on anything bought over the counter.
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_CODE_128,
        )
        // A data matrix on a medicine box is only a few millimetres across.
        .enableAutoZoom()
        // Lets the number be typed in when a scuffed or curved box will not scan.
        .allowManualInput()
        .build()

    // On a phone without Play services the scanner classes can fail to link at all, which has to
    // surface as a message rather than as a crash.
    val host = context.findActivity() ?: context

    runCatching {
        GmsBarcodeScanning.getClient(host, options)
            .startScan()
            .addOnSuccessListener { barcode ->
                onResult(ScanOutcome.Scanned(Gs1Parser.parse(barcode.rawValue)))
            }
            .addOnCanceledListener { onResult(ScanOutcome.Cancelled) }
            .addOnFailureListener { onResult(it.toOutcome()) }
    }.onFailure { thrown ->
        // Only a missing class really means "no Play services here". Anything else is reported
        // as itself, with the reason attached - this app has no logcat to fall back on.
        val missing = thrown is NoClassDefFoundError || thrown is ClassNotFoundException
        onResult(
            if (missing) ScanOutcome.Failed(ScanError.PLAY_SERVICES_MISSING)
            else ScanOutcome.Failed(ScanError.UNKNOWN, thrown.describe()),
        )
    }
}

/** Short, human-readable reason, shown under the error so a failure can be reported back. */
private fun Throwable.describe(): String {
    val code = (this as? MlKitException)?.errorCode?.let { "code $it " }.orEmpty()
    return "$code${this::class.java.simpleName}: ${message ?: "-"}"
}

/**
 * Backing out of the scanner arrives as a failure rather than a cancellation on some versions, so
 * it is turned back into [ScanOutcome.Cancelled] here - closing the viewfinder should not put an
 * error message on screen.
 */
private fun Throwable.toOutcome(): ScanOutcome {
    if (this !is MlKitException) return ScanOutcome.Failed(ScanError.UNKNOWN, describe())
    return when (errorCode) {
        MlKitException.CODE_SCANNER_CANCELLED -> ScanOutcome.Cancelled
        MlKitException.CODE_SCANNER_UNAVAILABLE,
        MlKitException.UNAVAILABLE,
        -> ScanOutcome.Failed(ScanError.MODULE_UNAVAILABLE, describe())
        MlKitException.PERMISSION_DENIED -> ScanOutcome.Failed(ScanError.CAMERA_UNAVAILABLE, describe())
        else -> ScanOutcome.Failed(ScanError.UNKNOWN, describe())
    }
}
