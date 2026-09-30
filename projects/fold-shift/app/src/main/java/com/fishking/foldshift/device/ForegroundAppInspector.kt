package com.fishking.foldshift.device

import com.fishking.foldshift.home.ShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the package currently in the foreground.
 *
 * We shell out to `dumpsys activity activities` through the Shizuku
 * channel and parse `topResumedActivity`. That parser is the same shape
 * we validated against the device during the earlier diagnosis, so we
 * keep it instead of relying on `ActivityManager.getRunningTasks`,
 * which modern Android restricts for third-party apps.
 */
class ForegroundAppInspector(private val shell: ShellExecutor) {

    suspend fun topPackage(): String? = withContext(Dispatchers.IO) {
        val output = shell.exec("dumpsys activity activities").stdout
        parseTopPackage(output)
    }

    internal fun parseTopPackage(output: String): String? {
        TOP_RESUMED.find(output)?.let { match ->
            return match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() }
        }
        RESUMED.find(output)?.let { match ->
            return match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() }
        }
        return null
    }

    internal companion object {
        /**
         * Matches:
         *   topResumedActivity=ActivityRecord{25755374 u0 bitpit.launcher/.ui.HomeActivity t5200}
         * Captures the package name before the `/`.
         */
        val TOP_RESUMED = Regex(
            """topResumedActivity=ActivityRecord\{[^}]*?\bu\d+\s+([\w.]+)/""",
        )

        /**
         * Fallback for builds that only report `mResumedActivity`.
         */
        val RESUMED = Regex(
            """mResumedActivity: ActivityRecord\{[^}]*?\bu\d+\s+([\w.]+)/""",
        )
    }
}