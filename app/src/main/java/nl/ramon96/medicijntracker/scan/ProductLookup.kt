package nl.ramon96.medicijntracker.scan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.ramon96.medicijntracker.BuildConfig
import nl.ramon96.medicijntracker.domain.barcode.ScannedCode
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Why a lookup could not tell us anything, phrased so the UI can show it straight to the user. */
enum class LookupError { NO_NETWORK, RATE_LIMITED, UNKNOWN }

/**
 * What a public database thinks is in the box.
 *
 * A suggestion, never a fact: coverage of pharmacy products is thin and the entries are written by
 * whoever scanned the product first. The UI has to put this in front of the user for checking
 * rather than saving it.
 */
data class ProductSuggestion(
    val name: String,
    val brand: String? = null,
    /** As written on the pack, e.g. "30 tabletten". */
    val quantity: String? = null,
)

sealed interface LookupResult {
    data class Found(val suggestion: ProductSuggestion) : LookupResult
    data object NotFound : LookupResult
    data class Failed(val error: LookupError) : LookupResult
}

/**
 * Asks Open Products Facts what a scanned barcode is.
 *
 * Open Products Facts is used because it needs no API key: this app is published as an APK on a
 * public releases page, so any key baked in would be readable by anyone who downloaded it. The
 * price is coverage - most Dutch pharmacy products are simply not in there, and the honest answer
 * to a scan will usually be [NotFound], which drops the user into filling the medicine in by hand.
 *
 * Written with `HttpURLConnection` and `org.json` because that is what the in-app updater already
 * uses; the project carries no HTTP or JSON library.
 */
class ProductLookup(
    private val userAgent: String = DEFAULT_USER_AGENT,
) {

    suspend fun lookup(code: ScannedCode): LookupResult = withContext(Dispatchers.IO) {
        // Open Products Facts keys on the barcode as printed, so a 13-digit pack has to be asked
        // for with 13 digits rather than the zero-padded GTIN-14 used internally.
        val barcode = code.ean13 ?: code.gtin ?: return@withContext LookupResult.NotFound

        val connection = runCatching {
            (URL("$BASE_URL/api/v2/product/$barcode.json?fields=$FIELDS").openConnection() as HttpURLConnection)
                .apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    // Open Food Facts rejects the default Java agent; it wants an app and a
                    // contact address.
                    setRequestProperty("User-Agent", userAgent)
                    // Shorter than the update check's: someone is watching a sheet, not a
                    // background job.
                    connectTimeout = 10_000
                    readTimeout = 10_000
                }
        }.getOrNull() ?: return@withContext LookupResult.Failed(LookupError.NO_NETWORK)

        try {
            when (val status = connection.responseCode) {
                200 -> parse(connection.inputStream.bufferedReader().use { it.readText() })
                404 -> LookupResult.NotFound
                429 -> LookupResult.Failed(LookupError.RATE_LIMITED)
                else -> LookupResult.Failed(
                    if (status in 500..599) LookupError.UNKNOWN else LookupError.UNKNOWN,
                )
            }
        } catch (_: Exception) {
            LookupResult.Failed(LookupError.NO_NETWORK)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val BASE_URL = "https://world.openproductsfacts.org"

        /** Asking for named fields keeps the reply a few hundred bytes instead of tens of KB. */
        private const val FIELDS = "product_name,product_name_nl,brands,quantity,generic_name"

        private val DEFAULT_USER_AGENT: String
            get() = "MijnMedicijnen/${BuildConfig.VERSION_NAME} " +
                "(https://github.com/${BuildConfig.UPDATE_REPO})"

        /**
         * Internal rather than private so the awkward part - deciding whether a 200 actually
         * contains a product - is covered by a plain JVM test.
         */
        internal fun parse(body: String): LookupResult {
            val json = runCatching { JSONObject(body) }.getOrNull()
                ?: return LookupResult.Failed(LookupError.UNKNOWN)

            // A missing product comes back as 200 with status 0, not as a 404.
            if (json.optInt("status", 0) != 1) return LookupResult.NotFound

            val product = json.optJSONObject("product") ?: return LookupResult.NotFound

            // Prefer the Dutch name: the box in her hand is Dutch.
            val name = listOf("product_name_nl", "product_name", "generic_name")
                .firstNotNullOfOrNull { product.optString(it).trim().takeIf { value -> value.isNotEmpty() } }
                ?: return LookupResult.NotFound

            return LookupResult.Found(
                ProductSuggestion(
                    name = name,
                    brand = product.optString("brands").trim().takeIf { it.isNotEmpty() },
                    quantity = product.optString("quantity").trim().takeIf { it.isNotEmpty() },
                ),
            )
        }
    }
}
