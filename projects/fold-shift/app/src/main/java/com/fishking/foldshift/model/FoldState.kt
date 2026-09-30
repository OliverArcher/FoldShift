package com.fishking.foldshift.model

/** Physical fold state we act on. */
enum class FoldState {
    /** Inner display is the active one; treat the device as unfolded. */
    OPENED,

    /** Cover display is the active one; treat the device as folded. */
    CLOSED,

    /**
     * We could not tell which display is active — for example during a
     * transition, while the screen is off, or on hardware that doesn't
     * expose the two built-in displays we expect. Treated as a no-op.
     */
    UNKNOWN,
}

/**
 * What the controller decided to do for a fold event. Kept as a value so
 * the service can log it and the UI can display it without re-deriving.
 */
sealed interface SwitchOutcome {
    /** Nothing to do (state unknown, already correct, or feature disabled). */
    data object NoOp : SwitchOutcome

    /** We asked the shell to change the default Home. */
    data class Switched(val target: String) : SwitchOutcome

    /** Default Home changed *and* we brought the new launcher to front. */
    data class SwitchedAndBroughtForward(val target: String) : SwitchOutcome

    /** Shell refused the change or verification failed. */
    data class Failed(val target: String, val reason: String) : SwitchOutcome
}