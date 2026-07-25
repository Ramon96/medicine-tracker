package nl.ramon96.medicijntracker.update

/**
 * Turns a release tag into the same number `app/build.gradle.kts` puts in the APK, so the app can
 * tell whether a GitHub release is actually newer than what is installed.
 *
 * Kept free of Android types so the comparison is covered by plain JVM tests - getting this wrong
 * either offers an endless update loop or silently never updates.
 */
object AppVersion {

    /** `v1.2.3` or `1.2.3` -> 10203. Returns null for anything that is not a version tag. */
    fun codeFromTag(tag: String?): Int? {
        val cleaned = tag?.trim()?.removePrefix("v")?.substringBefore('-') ?: return null
        val parts = cleaned.split('.')
        if (parts.size != 3) return null
        return runCatching {
            val major = parts[0].toInt()
            val minor = parts[1].toInt()
            val patch = parts[2].toInt()
            if (major < 0 || minor !in 0..99 || patch !in 0..99) return null
            major * 10_000 + minor * 100 + patch
        }.getOrNull()
    }

    /** True when [tag] describes a release newer than [installedVersionCode]. */
    fun isNewerThan(tag: String?, installedVersionCode: Int): Boolean {
        val candidate = codeFromTag(tag) ?: return false
        return candidate > installedVersionCode
    }
}

/** A release that is newer than the running build. */
data class UpdateInfo(
    val tag: String,
    val versionName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val releaseNotes: String,
)
