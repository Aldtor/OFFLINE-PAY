package com.offlinepay.feature.ussd

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Represents the lifecycle state of a USSD payment session.
 *
 * State flow:
 *  IDLE → REQUESTING → ACTIVE → RESPONSE_RECEIVED → COMPLETED
 *                                                  → FAILED
 *                     → TIMEOUT
 *                     → CANCELLED
 *
 * Design reference: Section 5 (USSD session state machine)
 * Requirements: Req 5.3 (session lifecycle), Req 5.7 (timeout handling)
 */
enum class UssdSessionState {
    IDLE,
    REQUESTING,
    ACTIVE,
    RESPONSE_RECEIVED,
    COMPLETED,
    TIMEOUT,
    FAILED,
    CANCELLED,
}

/**
 * State machine that manages transitions between [UssdSessionState] values
 * and enforces a configurable inactivity timeout.
 *
 * A [CoroutineScope] backed by [Dispatchers.Default] is owned by this machine.
 * Call [cancel] to release coroutine resources when the session ends.
 *
 * @param onStateChanged Invoked synchronously on every state transition.
 * @param onTimeout Invoked (from coroutine context) when the timeout fires.
 * @param timeoutSeconds Seconds before a TIMEOUT is triggered. Defaults to 30.
 */
class UssdSessionStateMachine(
    private val onStateChanged: (UssdSessionState) -> Unit,
    private val onTimeout: () -> Unit,
    private val timeoutSeconds: Int = 30,
) {
    private var _state = UssdSessionState.IDLE
    val state: UssdSessionState get() = _state

    private var timeoutJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Transitions the machine to [newState] and fires state-dependent side-effects.
     */
    fun transition(newState: UssdSessionState) {
        _state = newState
        onStateChanged(newState)
        when (newState) {
            UssdSessionState.REQUESTING -> startTimer()
            UssdSessionState.ACTIVE     -> resetTimer()
            UssdSessionState.COMPLETED,
            UssdSessionState.FAILED,
            UssdSessionState.TIMEOUT,
            UssdSessionState.CANCELLED  -> stopTimer()
            else                        -> Unit
        }
    }

    private fun startTimer() {
        stopTimer()
        timeoutJob = scope.launch {
            delay(timeoutSeconds * 1_000L)
            transition(UssdSessionState.TIMEOUT)
            onTimeout()
        }
    }

    /** Restarts the timer, e.g. when a new USSD response arrives. */
    private fun resetTimer() {
        startTimer()
    }

    private fun stopTimer() {
        timeoutJob?.cancel()
        timeoutJob = null
    }

    /**
     * Cancels the session and releases all coroutine resources owned by this machine.
     * The machine must not be used after calling this method.
     */
    fun cancel() {
        transition(UssdSessionState.CANCELLED)
        scope.cancel()
    }
}
