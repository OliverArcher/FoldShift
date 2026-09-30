package com.fishking.foldshift.home

import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.util.Log
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku

/**
 * Runs shell commands as the Shizuku / Stellar user.
 *
 * Rather than registering a `Shizuku.UserService` (which Stellar's
 * compatibility layer validates more strictly than upstream Shizuku),
 * we talk to the `IShizukuService` binder directly and call its
 * `newProcess` method. That gives us an `IRemoteProcess` backed by a
 * real `sh` process running with the shell identity.
 *
 * Requires the user to grant the Shizuku API permission to FoldShift.
 * Stellar exposes the same binder surface in compatibility mode, so the
 * same code path works against either provider.
 */
class ShizukuShellExecutor : ShellExecutor {

    override fun exec(command: String): ShellResult {
        if (!isBinderAvailable()) {
            return ShellResult(
                stdout = "",
                stderr = "Shizuku is not available; install Shizuku or Stellar in compatibility mode",
                exitCode = -1,
            )
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            return ShellResult(
                stdout = "",
                stderr = "Shizuku permission not granted; ask the user to authorise FoldShift",
                exitCode = -1,
            )
        }
        return try {
            val service = IShizukuService.Stub.asInterface(Shizuku.getBinder())
            val process = service.newProcess(arrayOf("sh", "-c", command), null, null)
            val stdout = readFully(process.inputStream)
            val stderr = readFully(process.errorStream)
            val exit = process.waitFor()
            ShellResult(stdout.trim(), stderr.trim(), exit)
        } catch (t: Throwable) {
            Log.w("FoldShift", "Shizuku exec failed for `$command`", t)
            ShellResult(stdout = "", stderr = t.message ?: "exec failed", exitCode = -1)
        }
    }

    private fun readFully(fd: ParcelFileDescriptor): String {
        return ParcelFileDescriptor.AutoCloseInputStream(fd)
            .bufferedReader()
            .use { it.readText() }
    }

    private fun isBinderAvailable(): Boolean = try {
        Shizuku.getBinder() != null
    } catch (t: Throwable) {
        Log.w("FoldShift", "Shizuku availability check failed", t)
        false
    }
}