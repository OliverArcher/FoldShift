package com.fishking.foldshift.home

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import com.fishking.foldshift.config.AppConfig

/**
 * Read and update the default Home (the launcher that owns
 * android.app.role.HOME). Phase 1 only exposes the read paths; Phase 3
 * wires in a real shell-backed write path.
 */
class DefaultHome(private val context: Context) {

    /**
     * Return the package that currently holds the HOME role, or `null` if
     * no role holder is set.
     *
     * We deliberately avoid [android.app.role.RoleManager] because its
     * getters require the signature|privileged `MANAGE_ROLE_HOLDERS`
     * permission. Instead we resolve the canonical HOME intent and read
     * the matching package from the system — which is the same data the
     * shell `cmd package resolve-activity` reports.
     */
    fun currentRoleHolder(): String? {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        val resolved = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?: return null
        return resolved.activityInfo?.packageName
    }

    /**
     * Enumerate every installed launcher the user can pick.
     *
     * Combines three sources so Samsung's quirks don't drop legitimate
     * launchers:
     *
     *   1. `queryIntentActivities(MAIN + HOME)` — the canonical source
     *      for "things that can be a default home". OneUI Home, Niagara,
     *      Autotools, the system FallbackHome all show up here.
     *   2. `LauncherApps.getActivityList` — returns activities with the
     *      standard `LAUNCHER` category (Autotools's separate
     *      `ActivityLaunchApp`, Niagara's HomeActivity, ...).
     *   3. `queryIntentActivities(MAIN + LAUNCHER_APP)` — Samsung's
     *      custom category. OneUI Home registers `LAUNCHER_APP` rather
     *      than `LAUNCHER`, so LauncherApps never returns it. Pulling
     *      `LAUNCHER_APP` separately recovers OneUI Home.
     *
     * The HOME list is then filtered to packages that show up in either
     * (2) or (3), which drops `com.android.settings/.FallbackHome`
     * (no LAUNCHER of any flavour) while keeping Autotools (LAUNCHER on
     * a different class than its HOME one) and OneUI Home.
     */
    fun listLaunchers(): List<LauncherCandidate> {
        val pm = context.packageManager

        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val homeResolved = try {
            pm.queryIntentActivities(homeIntent, 0)
        } catch (t: Throwable) {
            return emptyList()
        }

        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val user = android.os.UserHandle.getUserHandleForUid(android.os.Process.myUid())
        val standardLauncherPackages: Set<String> = try {
            launcherApps?.getActivityList(null, user).orEmpty()
                .map { it.applicationInfo.packageName }
                .toSet()
        } catch (t: Throwable) {
            emptySet()
        }
        val samsungLauncherPackages: Set<String> = try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory("android.intent.category.LAUNCHER_APP")
            }
            pm.queryIntentActivities(intent, 0)
                .map { it.activityInfo.packageName }
                .toSet()
        } catch (t: Throwable) {
            emptySet()
        }
        val launcherPackages = standardLauncherPackages + samsungLauncherPackages

        return homeResolved
            .asSequence()
            .filter { info ->
                val pkg = info.activityInfo.packageName
                val name = info.activityInfo.name
                // Drop the system FallbackHome placeholder — it has no
                // LAUNCHER, but `LAUNCHER_APP` exclusion alone is not
                // future-proof if Samsung ever adds one.
                if (pkg == "com.android.settings" &&
                    name.endsWith(".FallbackHome")
                ) return@filter false
                pkg in launcherPackages
            }
            .map { info ->
                LauncherCandidate(
                    packageName = info.activityInfo.packageName,
                    label = info.loadLabel(pm).toString(),
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /**
     * Write the default Home through the provided [ShellExecutor]. The
     * caller is responsible for choosing the executor; the default
     * constructor uses the [LocalShellExecutor] which is only meaningful in
     * tests or when ADB is relaying commands.
     */
    fun switchTo(packageName: String, executor: ShellExecutor = LocalShellExecutor()): Boolean {
        val result = executor.exec("cmd package set-home-activity --user 0 $packageName")
        val success = result.isSuccess && result.stdout.contains("Success")
        // Re-verify via the role manager so we never silently trust the
        // shell output — this is the source of truth Android exposes to
        // apps.
        return success && currentRoleHolder() == packageName
    }

    companion object {
        fun defaultExecutor(): ShellExecutor = LocalShellExecutor()

        /** Convenience used by the UI; not strictly needed but handy. */
        val DEFAULT_OUTER = AppConfig.DEFAULT_OUTER_PACKAGE
        val DEFAULT_INNER = AppConfig.DEFAULT_INNER_PACKAGE
    }
}

data class LauncherCandidate(
    val packageName: String,
    val label: String,
)