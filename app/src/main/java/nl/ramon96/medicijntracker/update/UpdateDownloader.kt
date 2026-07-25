package nl.ramon96.medicijntracker.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

sealed interface DownloadProgress {
    /** 0f..1f, or null while the server has not told us the total size. */
    data class Running(val fraction: Float?) : DownloadProgress
    data class Done(val file: File) : DownloadProgress
    data class Failed(val error: UpdateError) : DownloadProgress
}

/**
 * Downloads a release APK and hands it to Android's package installer.
 *
 * The install itself is the system's job - the app only supplies the file. Android then shows its
 * own confirmation, which is exactly the prompt a sideloaded update should go through.
 */
class UpdateDownloader(private val context: Context) {

    fun download(info: UpdateInfo): Flow<DownloadProgress> = flow {
        emit(DownloadProgress.Running(null))

        val target = File(updateDir(), "medicijntracker-${info.tag}.apk")
        // A half-finished file from a previous attempt would install as a corrupt APK.
        if (target.exists()) target.delete()

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(info.downloadUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 20_000
                readTimeout = 60_000
            }
            if (connection.responseCode !in 200..299) {
                emit(DownloadProgress.Failed(UpdateError.UNKNOWN))
                return@flow
            }

            val total = connection.contentLengthLong.takeIf { it > 0 }
            var written = 0L

            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        emit(DownloadProgress.Running(total?.let { written.toFloat() / it }))
                    }
                }
            }
            emit(DownloadProgress.Done(target))
        } catch (_: Exception) {
            target.delete()
            emit(DownloadProgress.Failed(UpdateError.NO_NETWORK))
        } finally {
            connection?.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    /** Opens the system installer for a downloaded APK. */
    fun install(file: File) {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** Old downloads are dead weight once a new one starts. */
    fun clearOldDownloads(keep: File? = null) {
        updateDir().listFiles()?.forEach { if (it != keep) it.delete() }
    }

    private fun updateDir(): File =
        File(context.cacheDir, "updates").apply { mkdirs() }
}
