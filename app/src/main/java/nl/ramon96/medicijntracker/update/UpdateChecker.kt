package nl.ramon96.medicijntracker.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.ramon96.medicijntracker.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Why a check could not tell us anything, phrased so the UI can show it straight to the user. */
enum class UpdateError { NO_NETWORK, RATE_LIMITED, NO_RELEASE, NO_APK, UNKNOWN }

sealed interface UpdateResult {
    data class Available(val info: UpdateInfo) : UpdateResult
    data object UpToDate : UpdateResult
    data class Failed(val error: UpdateError) : UpdateResult
}

/**
 * Asks GitHub for the newest release of this repo. Uses the public API without a token, so it is
 * rate-limited per IP - fine for a manual button and a weekly background check.
 */
class UpdateChecker(
    private val repo: String = BuildConfig.UPDATE_REPO,
    private val installedVersionCode: Int = BuildConfig.VERSION_CODE,
) {

    suspend fun check(): UpdateResult = withContext(Dispatchers.IO) {
        val connection = runCatching {
            (URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection)
                .apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/vnd.github+json")
                    connectTimeout = 15_000
                    readTimeout = 15_000
                }
        }.getOrNull() ?: return@withContext UpdateResult.Failed(UpdateError.NO_NETWORK)

        try {
            val body = when (val code = connection.responseCode) {
                200 -> connection.inputStream.bufferedReader().use { it.readText() }
                403, 429 -> return@withContext UpdateResult.Failed(UpdateError.RATE_LIMITED)
                404 -> return@withContext UpdateResult.Failed(UpdateError.NO_RELEASE)
                else -> return@withContext UpdateResult.Failed(
                    if (code in 500..599) UpdateError.UNKNOWN else UpdateError.UNKNOWN,
                )
            }
            parse(body)
        } catch (_: Exception) {
            UpdateResult.Failed(UpdateError.NO_NETWORK)
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(body: String): UpdateResult {
        val json = runCatching { JSONObject(body) }.getOrNull()
            ?: return UpdateResult.Failed(UpdateError.UNKNOWN)

        val tag = json.optString("tag_name").takeIf { it.isNotBlank() }
            ?: return UpdateResult.Failed(UpdateError.NO_RELEASE)

        if (!AppVersion.isNewerThan(tag, installedVersionCode)) return UpdateResult.UpToDate

        val assets = json.optJSONArray("assets") ?: return UpdateResult.Failed(UpdateError.NO_APK)
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name")
            if (!name.endsWith(".apk", ignoreCase = true)) continue
            val url = asset.optString("browser_download_url").takeIf { it.isNotBlank() } ?: continue
            return UpdateResult.Available(
                UpdateInfo(
                    tag = tag,
                    versionName = tag.removePrefix("v"),
                    downloadUrl = url,
                    sizeBytes = asset.optLong("size", 0L),
                    releaseNotes = json.optString("body").orEmpty().trim(),
                ),
            )
        }
        // A release exists and is newer, but nobody attached an APK to it.
        return UpdateResult.Failed(UpdateError.NO_APK)
    }
}
