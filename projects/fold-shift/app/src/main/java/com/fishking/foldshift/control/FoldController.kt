package com.fishking.foldshift.control

import android.util.Log
import com.fishking.foldshift.accessibility.FoldAccessibilityService
import com.fishking.foldshift.device.ForegroundAppInspector
import com.fishking.foldshift.home.DefaultHome
import com.fishking.foldshift.home.ShellExecutor
import com.fishking.foldshift.model.FoldState
import com.fishking.foldshift.model.SwitchOutcome
import kotlinx.coroutines.delay

/**
 * Decides what to do for a fold event and applies it.
 *
 * Rules (from the spec):
 *  - state OPENED  -> default Home = inner launcher
 *  - state CLOSED  -> default Home = outer launcher
 *  - anything else -> leave the default Home alone
 *
 * Changing the default Home never touches the foreground app, so we
 * always perform that step. Bringing a launcher to the front is only
 * done when the foreground is *already* one of the launchers — we never
 * pull the desktop over a third-party app.
 *
 * The bring-to-front step runs as the shell user via the Shizuku
 * channel. Using a regular `startActivity` from our own App context
 * with `setPackage(target)` was rejected by the target launchers
 * (verified on One UI Home and Niagara Launcher), so the shell-driven
 * path is the reliable way to actually take focus.
 */
class FoldController(
    private val home: DefaultHome,
    private val shell: ShellExecutor,
    private val inspector: ForegroundAppInspector,
) {

    private companion object {
        /** Settle window after a launch / keyevent before we re-read
         *  the foreground. Generous enough to cover slow One UI Home
         *  resume animations; small enough that the user doesn't
         *  notice the extra delay on a fold event. */
        const val POST_DISPATCH_SETTLE_MS = 60L
    }

    suspend fun handle(
        state: FoldState,
        innerPackage: String,
        outerPackage: String,
    ): SwitchOutcome {
        val target = when (state) {
            FoldState.OPENED -> innerPackage
            FoldState.CLOSED -> outerPackage
            FoldState.UNKNOWN -> return SwitchOutcome.NoOp
        }.takeIf { it.isNotBlank() } ?: return SwitchOutcome.NoOp

        val current = home.currentRoleHolder()
        if (current != target) {
            val switched = home.switchTo(target, shell)
            if (!switched) {
                return SwitchOutcome.Failed(target, "set-home-activity did not take effect")
            }
        }

        return bringForwardIfOnLauncher(target, innerPackage, outerPackage)
    }

    private suspend fun bringForwardIfOnLauncher(
        target: String,
        innerPackage: String,
        outerPackage: String,
    ): SwitchOutcome {
        val foreground = inspector.topPackage()
        Log.i("FoldShift", "fold decision: target=$target foreground=$foreground")

        val foregroundIsLauncher = foreground != null &&
            (foreground == innerPackage || foreground == outerPackage)

        if (!foregroundIsLauncher || foreground == target) {
            return SwitchOutcome.Switched(target)
        }

        return if (launchHomeViaShell(target)) {
            SwitchOutcome.SwitchedAndBroughtForward(target)
        } else {
            SwitchOutcome.Switched(target)
        }
    }

    /**
     * Launches the target launcher as the shell user.
     *
     * After `cmd package set-home-activity` we need the new launcher to
     * actually take foreground. Two failure modes have been observed on
     * One UI:
     *
     *   - `am start -c HOME` keeps resolving to the previous launcher
     *     because the HOME intent filter lags the role holder change.
     *   - Even an explicit `am start -n <component>` can succeed yet
     *     leave the previous launcher on screen if its task wins the
     *     z-order fight.
     *
     * Both of these land on the same root cause: the launch is
     * attributed to our app's UID and Android 12+ Background Activity
     * Launch (BAL) refuses it. The HOME global action bypasses BAL
     * because the dispatch is attributed to system_server, so we try
     * the privileged paths first.
     *
     * Order of attempts:
     *   1. [FoldAccessibilityService.performHome] — fastest, requires
     *      the user to enable FoldShift under Settings → Accessibility.
     *   2. `input keyevent KEYCODE_HOME` via shizuku shell — works
     *      without extra permissions; verified working on this device.
     *   3. `am start -n <resolved component>` — bypasses HOME intent
     *      resolution. Still hits BAL on this device, so it's only a
     *      last-resort safety net.
     *
     * Each attempt is verified against `topResumedActivity` before we
     * claim success, so the [SwitchOutcome] we surface never lies about
     * what actually reached the foreground.
     */
    private suspend fun launchHomeViaShell(target: String): Boolean {
        // 1. AccessibilityService — shortest path when enabled.
        if (FoldAccessibilityService.isReady()) {
            if (FoldAccessibilityService.performHome()) {
                delay(POST_DISPATCH_SETTLE_MS)
                val foreground = inspector.topPackage()
                if (foreground == target) {
                    Log.i("FoldShift", "accessibility HOME landed on $target")
                    return true
                }
                Log.w(
                    "FoldShift",
                    "accessibility HOME: foreground=$foreground (expected $target); trying keyevent",
                )
            } else {
                Log.w("FoldShift", "accessibility performGlobalAction returned false")
            }
        }

        // 2. Shizuku-shell HOME keyevent — verified working on this device.
        val home = shell.exec("input keyevent KEYCODE_HOME")
        if (home.isSuccess) {
            delay(POST_DISPATCH_SETTLE_MS)
            val foreground = inspector.topPackage()
            if (foreground == target) {
                Log.i("FoldShift", "HOME keyevent landed on $target")
                return true
            }
            Log.w(
                "FoldShift",
                "HOME keyevent: foreground=$foreground (expected $target); trying component launch",
            )
        } else {
            Log.w("FoldShift", "input keyevent HOME failed: ${home.stderr}")
        }

        // 3. Explicit component launch — bypasses HOME intent resolution.
        //    Last resort; BAL typically still blocks this on One UI.
        val resolve = shell.exec(
            "cmd package resolve-activity --brief " +
                "-a android.intent.action.MAIN " +
                "-c android.intent.category.LAUNCHER " +
                target
        )
        val component = resolve.stdout
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && "/" in it }

        if (component == null) {
            Log.w(
                "FoldShift",
                "could not resolve LAUNCHER activity for $target: " +
                    "exit=${resolve.exitCode} stdout='${resolve.stdout}' stderr='${resolve.stderr}'",
            )
            return false
        }

        val cmd = "am start -W -n $component --activity-clear-top"
        val result = shell.exec(cmd)
        if (!result.isSuccess || !result.stdout.contains("Status: ok")) {
            Log.w(
                "FoldShift",
                "$cmd failed: exit=${result.exitCode} stdout=${result.stdout} stderr=${result.stderr}",
            )
            return false
        }

        delay(POST_DISPATCH_SETTLE_MS)
        val foreground = inspector.topPackage()
        if (foreground == target) {
            Log.i("FoldShift", "component launch landed on $target via $component")
            return true
        }
        Log.w(
            "FoldShift",
            "$cmd reported ok but foreground=$foreground (expected $target)",
        )
        return false
    }
}