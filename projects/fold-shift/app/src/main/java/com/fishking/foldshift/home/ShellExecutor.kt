package com.fishking.foldshift.home

import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Common abstraction for "run a shell command as the shell user". Phase 1
 * ships a debug implementation that simply runs commands inside the App
 * process via [Runtime.exec]; this is *not* privileged on most devices and
 * is only kept around so unit tests have something to bind to.
 *
 * Phase 3 will plug in:
 *   - ShizukuShellExecutor (runs commands under the adb shell user via the
 *     Shizuku API when the user opts in)
 *   - AdbOverWifiShellExecutor (talks to a paired host PC if the user would
 *     rather drive switching from the desktop)
 *
 * The interface intentionally hides that detail so the controller and the
 * settings UI never need to know which executor is in play.
 */
interface ShellExecutor {
    /**
     * Run a single shell command and return its trimmed stdout. The result
     * returned by Android for `cmd package set-home-activity` is the literal
     * string "Success" on success; callers compare it explicitly.
     *
     * Implementations must not throw on non-zero exit codes; instead they
     * return the captured output (which Android uses to signal failures).
     */
    fun exec(command: String): ShellResult
}

/** Result of a shell command execution. */
data class ShellResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
) {
    val isSuccess: Boolean get() = exitCode == 0
}

/**
 * Local fallback. Only useful while ADB is forwarding the call or for tests.
 * Returns an empty success when the device doesn't grant the App any shell
 * privileges — callers must still verify the actual HOME role afterwards.
 */
class LocalShellExecutor : ShellExecutor {
    override fun exec(command: String): ShellResult {
        val process = try {
            Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
        } catch (t: Throwable) {
            return ShellResult(stdout = "", stderr = t.message ?: "exec failed", exitCode = -1)
        }
        val stdout = process.inputStream.bufferedReader().use(BufferedReader::readText)
        val stderr = process.errorStream.bufferedReader().use(BufferedReader::readText)
        val exit = process.waitFor()
        return ShellResult(stdout.trim(), stderr.trim(), exit)
    }
}