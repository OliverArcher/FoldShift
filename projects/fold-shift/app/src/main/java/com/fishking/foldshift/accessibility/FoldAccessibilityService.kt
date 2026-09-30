package com.fishking.foldshift.accessibility

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Thin accessibility service used solely as a privileged dispatcher for
 * the HOME global action. We do not observe or modify accessibility
 * events — the service is just a back door around Android 12+ Background
 * Activity Launch (BAL), because the HOME dispatch it triggers is
 * attributed to system_server (UID 1000) instead of our app's UID.
 *
 * The user must enable the service in Settings → Accessibility. When it
 * is disabled, [performHome] simply returns false and the controller
 * falls back to the slower shizuku-driven `input keyevent` path.
 */
class FoldAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i("FoldShift", "Accessibility service connected")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        Log.i("FoldShift", "Accessibility service unbound")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No-op: we don't observe events, only perform global actions.
    }

    override fun onInterrupt() {
        // No-op.
    }

    companion object {
        @Volatile
        private var instance: FoldAccessibilityService? = null

        /** True if the service is currently bound and ready to dispatch. */
        fun isReady(): Boolean = instance != null

        /**
         * Dispatch HOME through the accessibility framework. Returns
         * `true` if the system accepted the action — it does not wait
         * for the resulting activity transition.
         */
        fun performHome(): Boolean {
            val service = instance ?: return false
            return try {
                service.performGlobalAction(GLOBAL_ACTION_HOME)
            } catch (t: Throwable) {
                Log.w("FoldShift", "performGlobalAction HOME failed", t)
                false
            }
        }
    }
}