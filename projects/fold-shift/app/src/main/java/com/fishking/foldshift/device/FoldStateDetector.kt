package com.fishking.foldshift.device

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import com.fishking.foldshift.model.FoldState

/**
 * Derives [FoldState] from the built-in displays.
 *
 * On this Galaxy Z Fold the two built-in panels behave like this:
 *   - inner:  ~2448 x 1848 (4.52 M px²), only active when unfolded.
 *            Rotates with the device, so width/height swap under
 *            portrait orientation, but the *area* is invariant.
 *   - cover:  ~1248 x 1972 (2.46 M px²), only active when folded.
 *
 * Only one of them is in [Display.STATE_ON] at a time. We pick the
 * powered-on display with the largest area and decide from the area,
 * not the aspect ratio. Earlier versions used aspect ratio, which
 * wrongly reported `CLOSED` whenever the unfolded device was held in
 * portrait (the inner panel becomes 1848 × 2448 in that orientation).
 *
 * This avoids the Samsung hinge-angle sensor entirely — that sensor
 * was observed reporting wild 0/180 flips during video playback,
 * which is what made the old Tasker setup oscillate.
 */
class FoldStateDetector(
    context: Context,
    private val onStateChanged: (FoldState) -> Unit,
) {
    private val displayManager: DisplayManager? =
        context.getSystemService(DisplayManager::class.java)

    private val listener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = emit()
        override fun onDisplayRemoved(displayId: Int) = emit()
        override fun onDisplayChanged(displayId: Int) = emit()
    }

    private var started = false

    /** Last state we forwarded to [onStateChanged]. Used to suppress
     *  duplicate display events that arrive while the panel handoff is
     *  still settling (cover/inner both in transition, screen-off
     *  housekeeping pings, etc.). */
    private var lastEmitted: FoldState? = null

    fun start() {
        if (started) return
        // Reset so the first emit() after start always propagates the
        // current physical state, even if we stopped at the same state
        // we are about to start at.
        lastEmitted = null
        displayManager?.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        started = true
        emit()
    }

    fun stop() {
        if (!started) return
        displayManager?.unregisterDisplayListener(listener)
        started = false
    }

    fun currentState(): FoldState {
        val displays = displayManager?.displays ?: return FoldState.UNKNOWN
        val onDisplays = displays.filter { it.state == Display.STATE_ON }
        if (onDisplays.isEmpty()) return FoldState.UNKNOWN

        val active = onDisplays.maxByOrNull { it.width.toLong() * it.height } ?: return FoldState.UNKNOWN
        val area = active.width.toLong() * active.height

        // Ignore tiny/virtual displays that some OEMs register.
        if (area < MIN_REAL_DISPLAY_AREA) return FoldState.UNKNOWN

        // Pick the active panel by area, not aspect ratio — the inner
        // display rotates with the device, so width/height swap
        // mid-use, but its area (~4.52 M px²) is always larger than
        // the cover display's (~2.46 M px²).
        return if (area >= INNER_DISPLAY_MIN_AREA) {
            FoldState.OPENED
        } else {
            FoldState.CLOSED
        }
    }

    private fun emit() {
        val state = currentState()
        if (state == lastEmitted) {
            Log.d("FoldShift", "Fold state unchanged ($state); skipping")
            return
        }
        lastEmitted = state
        Log.i("FoldShift", "Fold state -> $state")
        onStateChanged(state)
    }

    private companion object {
        /** ~1.5 M px²; the cover display alone is ~2.46 M px². Anything
         *  smaller is a virtual/OEM-registered panel, not the real
         *  cover or inner. */
        private const val MIN_REAL_DISPLAY_AREA = 1_500_000L

        /** ~3.5 M px²; sits comfortably between the inner panel's
         *  ~4.52 M px² and the cover's ~2.46 M px². Any active panel
         *  with area >= this is the unfolded inner display. */
        private const val INNER_DISPLAY_MIN_AREA = 3_500_000L
    }
}