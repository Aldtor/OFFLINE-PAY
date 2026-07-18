package com.offlinepay.feature.pay123

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

/**
 * Represents the lifecycle state of a 123PAY IVR payment session.
 *
 * State flow (design Section 7.2 / 12.x):
 *
 *  IDLE → INITIATING_CALL → CALL_IN_PROGRESS → AWAITING_USER_CONFIRMATION
 *                         → FAILED (call blocked immediately)
 *  AWAITING_USER_CONFIRMATION → SUCCESS
 *                             → FAILED
 *                             → UNKNOWN
 *  SUCCESS / FAILED / UNKNOWN → IDLE (after result is persisted)
 *
 * Requirements: Req 6.1–6.8
 */
enum class Pay123SessionState {
    /** No active payment session. Initial and final state. */
    IDLE,

    /**
     * `ACTION_CALL` intent has been dispatched; waiting for the app to be
     * backgrounded (confirmation that the call was placed).
     */
    INITIATING_CALL,

    /**
     * App is in the background — IVR call is assumed to be in progress.
     * Entered via `onPause()` after a successful call launch.
     */
    CALL_IN_PROGRESS,

    /**
     * User has returned to the app (`onResume()` detected).
     * Waiting for the user to confirm the payment outcome.
     */
    AWAITING_USER_CONFIRMATION,

    /** User confirmed the payment succeeded; transaction updated to SUCCESS. */
    SUCCESS,

    /**
     * Call was blocked or user confirmed the payment failed;
     * transaction updated to FAILURE.
     */
    FAILED,

    /**
     * User dismissed the confirmation prompt without answering;
     * transaction status remains indeterminate (UNKNOWN).
     */
    UNKNOWN,
}

/**
 * State machine that manages transitions between [Pay123SessionState] values.
 *
 * ### Lifecycle integration
 * The machine registers itself as a [DefaultLifecycleObserver] on the provided
 * [lifecycle].  When the lifecycle owner pauses (app goes to background after
 * call launch), the machine automatically transitions
 * `INITIATING_CALL → CALL_IN_PROGRESS`.  When the lifecycle owner resumes
 * (user returns from the IVR call), the machine automatically transitions
 * `CALL_IN_PROGRESS → AWAITING_USER_CONFIRMATION`.
 *
 * ### Duplicate-tap prevention
 * Once [confirmResult] is called, [isResultConfirmed] is set to `true`.
 * Subsequent calls to [confirmResult] are no-ops, preventing double-confirmation
 * caused by rapid taps or configuration changes.
 *
 * @param onStateChanged Invoked synchronously on every valid state transition.
 * @param lifecycle      [Lifecycle] to observe for pause/resume signals.
 */
class Pay123SessionStateMachine(
    private val onStateChanged: (Pay123SessionState) -> Unit,
    lifecycle: Lifecycle,
) : DefaultLifecycleObserver {

    private var _state: Pay123SessionState = Pay123SessionState.IDLE
    val state: Pay123SessionState get() = _state

    /**
     * Guard flag that prevents duplicate confirmation taps from re-triggering
     * a state transition once a terminal result (SUCCESS / FAILED / UNKNOWN)
     * has been confirmed by the user.
     *
     * Design reference: Section 7.4 (Duplicate confirmation row)
     * Requirements: Req 6.6–6.8
     */
    var isResultConfirmed: Boolean = false
        private set

    /**
     * Set to `true` once [initiate] has been called, so that the lifecycle
     * observer ignores spurious `onResume()` calls that happen before the
     * IVR call is actually launched.
     *
     * Design reference: Section 7.1 (isCallInitiated flag)
     */
    private var isCallInitiated: Boolean = false

    init {
        lifecycle.addObserver(this)
    }

    // ── DefaultLifecycleObserver ────────────────────────────────────────────

    /**
     * App has gone to background.  If we are in [Pay123SessionState.INITIATING_CALL]
     * this means the IVR call was successfully launched, so transition to
     * [Pay123SessionState.CALL_IN_PROGRESS].
     */
    override fun onPause(owner: LifecycleOwner) {
        if (_state == Pay123SessionState.INITIATING_CALL && isCallInitiated) {
            transition(Pay123SessionState.CALL_IN_PROGRESS)
        }
    }

    /**
     * User returned to the app.  If we are in [Pay123SessionState.CALL_IN_PROGRESS]
     * this means the IVR call has ended and we should prompt for the result.
     */
    override fun onResume(owner: LifecycleOwner) {
        if (_state == Pay123SessionState.CALL_IN_PROGRESS) {
            transition(Pay123SessionState.AWAITING_USER_CONFIRMATION)
        }
    }

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Marks the call as initiated and transitions from [Pay123SessionState.IDLE]
     * to [Pay123SessionState.INITIATING_CALL].
     *
     * Must only be called once per session from [Pay123SessionState.IDLE].
     */
    fun initiate() {
        check(_state == Pay123SessionState.IDLE) {
            "initiate() called in invalid state $_state"
        }
        isCallInitiated = true
        transition(Pay123SessionState.INITIATING_CALL)
    }

    /**
     * Signals that the ACTION_CALL intent was blocked or failed immediately,
     * transitioning to [Pay123SessionState.FAILED].
     *
     * Valid only from [Pay123SessionState.INITIATING_CALL].
     */
    fun reportCallFailed() {
        if (_state == Pay123SessionState.INITIATING_CALL) {
            transition(Pay123SessionState.FAILED)
        }
    }

    /**
     * Confirms the terminal result of the IVR session.
     *
     * Protected by [isResultConfirmed]: if this has already been called once,
     * subsequent calls are silently ignored (duplicate-tap guard).
     *
     * @param result The user-reported outcome ([Pay123SessionState.SUCCESS],
     *               [Pay123SessionState.FAILED], or [Pay123SessionState.UNKNOWN]).
     */
    fun confirmResult(result: Pay123SessionState) {
        require(
            result == Pay123SessionState.SUCCESS ||
                result == Pay123SessionState.FAILED ||
                result == Pay123SessionState.UNKNOWN,
        ) { "confirmResult() only accepts SUCCESS, FAILED, or UNKNOWN; got $result" }

        if (isResultConfirmed) return // Duplicate-tap guard

        if (_state == Pay123SessionState.AWAITING_USER_CONFIRMATION) {
            isResultConfirmed = true
            transition(result)
        }
    }

    /**
     * Resets the machine to [Pay123SessionState.IDLE] after a terminal state
     * has been processed (result persisted and navigation triggered).
     */
    fun reset() {
        isResultConfirmed = false
        isCallInitiated = false
        _state = Pay123SessionState.IDLE
        onStateChanged(Pay123SessionState.IDLE)
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private fun transition(newState: Pay123SessionState) {
        _state = newState
        onStateChanged(newState)
    }
}
