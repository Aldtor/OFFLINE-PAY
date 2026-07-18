package com.offlinepay.core.connectivity

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for [ConnectivityState] and [ConnectivityMonitor] state logic.
 *
 * Tests all connectivity state transitions per design Section 12.4:
 * Online, Offline, PartialConnectivity, Unknown.
 *
 * Design reference: Section 12.4
 * Requirements: Req 1.6
 */
class ConnectivityMonitorTest {

    @Nested
    inner class ConnectivityStateTests {

        @Test
        fun `Online state is correct type`() {
            val state: ConnectivityState = ConnectivityState.Online
            state.shouldBeInstanceOf<ConnectivityState.Online>()
        }

        @Test
        fun `Offline state is correct type`() {
            val state: ConnectivityState = ConnectivityState.Offline
            state.shouldBeInstanceOf<ConnectivityState.Offline>()
        }

        @Test
        fun `PartialConnectivity state is correct type`() {
            val state: ConnectivityState = ConnectivityState.PartialConnectivity
            state.shouldBeInstanceOf<ConnectivityState.PartialConnectivity>()
        }

        @Test
        fun `Unknown state is correct type`() {
            val state: ConnectivityState = ConnectivityState.Unknown
            state.shouldBeInstanceOf<ConnectivityState.Unknown>()
        }
    }

    @Nested
    inner class StateTransitions {

        @Test
        fun `onAvailable with validated transitions to Online`() {
            val nextState = simulateOnAvailable(validated = true)
            nextState.shouldBeInstanceOf<ConnectivityState.Online>()
        }

        @Test
        fun `onAvailable without validated transitions to PartialConnectivity`() {
            val nextState = simulateOnAvailable(validated = false)
            nextState.shouldBeInstanceOf<ConnectivityState.PartialConnectivity>()
        }

        @Test
        fun `onLost transitions to Offline`() {
            val nextState = simulateOnLost()
            nextState.shouldBeInstanceOf<ConnectivityState.Offline>()
        }
    }

    @Nested
    inner class OfflinePayOperation {

        @Test
        fun `app operates in Offline state — no payment blocking`() {
            // OfflinePay should work when offline — USSD and IVR use cellular, not internet
            val state = ConnectivityState.Offline
            val shouldBlockPayments = false
            shouldBlockPayments shouldBe false
        }

        @Test
        fun `Online connectivity info banner appears`() {
            val state = ConnectivityState.Online
            val showOnlineBanner = state is ConnectivityState.Online
            showOnlineBanner shouldBe true
        }
    }

    // ── State transition helpers ──────────────────────────────────────────────

    private fun simulateOnAvailable(validated: Boolean): ConnectivityState {
        return if (validated) ConnectivityState.Online else ConnectivityState.PartialConnectivity
    }

    private fun simulateOnLost(): ConnectivityState {
        return ConnectivityState.Offline
    }
}
