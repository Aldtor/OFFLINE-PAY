package com.offlinepay.feature.ussd

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [UssdSessionStateMachine] transitions.
 *
 * Verifies the 7-state machine:
 * IDLE → REQUESTING → ACTIVE → RESPONSE_RECEIVED → COMPLETED/TIMEOUT/FAILED/CANCELLED
 *
 * Design reference: Section 6.2 / 12.2
 * Requirements: Req 5.6 (timeout guard), Req 5.4 (cancel)
 */
class UssdSessionStateMachineTest {

    private lateinit var stateMachine: UssdSessionStateMachine

    @BeforeEach
    fun setup() {
        stateMachine = UssdSessionStateMachine()
    }

    // ── Happy path ───────────────────────────────────────────────────────────

    @Test
    fun `initial state is IDLE`() {
        stateMachine.currentState shouldBe UssdSessionState.IDLE
    }

    @Test
    fun `IDLE to REQUESTING transition`() {
        stateMachine.transition(UssdSessionEvent.REQUEST_SENT)
        stateMachine.currentState shouldBe UssdSessionState.REQUESTING
    }

    @Test
    fun `REQUESTING to ACTIVE on response received`() {
        stateMachine.transition(UssdSessionEvent.REQUEST_SENT)
        stateMachine.transition(UssdSessionEvent.SESSION_ACTIVE)
        stateMachine.currentState shouldBe UssdSessionState.ACTIVE
    }

    @Test
    fun `ACTIVE to COMPLETED on success`() {
        stateMachine.transition(UssdSessionEvent.REQUEST_SENT)
        stateMachine.transition(UssdSessionEvent.SESSION_ACTIVE)
        stateMachine.transition(UssdSessionEvent.SUCCESS_RESPONSE)
        stateMachine.currentState shouldBe UssdSessionState.COMPLETED
    }

    // ── Timeout path ─────────────────────────────────────────────────────────

    @Test
    fun `REQUESTING to TIMEOUT on timeout event`() {
        stateMachine.transition(UssdSessionEvent.REQUEST_SENT)
        stateMachine.transition(UssdSessionEvent.TIMEOUT)
        stateMachine.currentState shouldBe UssdSessionState.TIMEOUT
    }

    @Test
    fun `ACTIVE to TIMEOUT on timeout event`() {
        stateMachine.transition(UssdSessionEvent.REQUEST_SENT)
        stateMachine.transition(UssdSessionEvent.SESSION_ACTIVE)
        stateMachine.transition(UssdSessionEvent.TIMEOUT)
        stateMachine.currentState shouldBe UssdSessionState.TIMEOUT
    }

    // ── Failure path ─────────────────────────────────────────────────────────

    @Test
    fun `ACTIVE to FAILED on error`() {
        stateMachine.transition(UssdSessionEvent.REQUEST_SENT)
        stateMachine.transition(UssdSessionEvent.SESSION_ACTIVE)
        stateMachine.transition(UssdSessionEvent.ERROR_RESPONSE)
        stateMachine.currentState shouldBe UssdSessionState.FAILED
    }

    // ── Cancel path ──────────────────────────────────────────────────────────

    @Test
    fun `ACTIVE to CANCELLED on user cancel`() {
        stateMachine.transition(UssdSessionEvent.REQUEST_SENT)
        stateMachine.transition(UssdSessionEvent.SESSION_ACTIVE)
        stateMachine.transition(UssdSessionEvent.USER_CANCELLED)
        stateMachine.currentState shouldBe UssdSessionState.CANCELLED
    }

    @Test
    fun `REQUESTING to CANCELLED on user cancel`() {
        stateMachine.transition(UssdSessionEvent.REQUEST_SENT)
        stateMachine.transition(UssdSessionEvent.USER_CANCELLED)
        stateMachine.currentState shouldBe UssdSessionState.CANCELLED
    }

    // ── Terminal state guards ─────────────────────────────────────────────────

    @Test
    fun `COMPLETED is terminal - no further transitions`() {
        stateMachine.transition(UssdSessionEvent.REQUEST_SENT)
        stateMachine.transition(UssdSessionEvent.SESSION_ACTIVE)
        stateMachine.transition(UssdSessionEvent.SUCCESS_RESPONSE)
        stateMachine.transition(UssdSessionEvent.ERROR_RESPONSE) // Should be ignored
        stateMachine.currentState shouldBe UssdSessionState.COMPLETED
    }

    @Test
    fun `FAILED is terminal - no further transitions`() {
        stateMachine.transition(UssdSessionEvent.REQUEST_SENT)
        stateMachine.transition(UssdSessionEvent.SESSION_ACTIVE)
        stateMachine.transition(UssdSessionEvent.ERROR_RESPONSE)
        stateMachine.transition(UssdSessionEvent.SUCCESS_RESPONSE) // Should be ignored
        stateMachine.currentState shouldBe UssdSessionState.FAILED
    }

    // ── Reset ────────────────────────────────────────────────────────────────

    @Test
    fun `reset returns to IDLE`() {
        stateMachine.transition(UssdSessionEvent.REQUEST_SENT)
        stateMachine.transition(UssdSessionEvent.SESSION_ACTIVE)
        stateMachine.reset()
        stateMachine.currentState shouldBe UssdSessionState.IDLE
    }
}
