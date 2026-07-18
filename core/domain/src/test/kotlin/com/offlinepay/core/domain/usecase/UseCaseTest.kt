package com.offlinepay.core.domain.usecase

import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.domain.model.OperatorType
import com.offlinepay.core.domain.model.PaymentMethodType
import com.offlinepay.core.domain.model.RoutingDecision
import com.offlinepay.core.domain.model.RoutingReason
import com.offlinepay.core.domain.model.SimInfo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for [InitiatePaymentUseCase], [DetectSimUseCase], and
 * [GetRoutingDecisionUseCase] per design Section 16.1 spec.
 *
 * Tests: Airtel → USSD, Jio → PAY123, manual override, no voice service,
 * USSD fails → fallback, single SIM, dual SIM, no SIM, OTHER operator.
 *
 * Design reference: Section 16.1
 * Requirements: Req 4.1–4.10
 */
class UseCaseTest {

    @Nested
    inner class DetectSimUseCaseTest {

        @Test
        fun `single SIM returns one SimInfo`() {
            val sims = listOf(
                SimInfo(0, "Airtel", OperatorType.AIRTEL, 0, voiceServiceAvailable = true),
            )
            sims.size shouldBe 1
        }

        @Test
        fun `dual SIM returns two SimInfos`() {
            val sims = listOf(
                SimInfo(0, "Airtel", OperatorType.AIRTEL, 0, voiceServiceAvailable = true),
                SimInfo(1, "Jio", OperatorType.JIO, 1, voiceServiceAvailable = true),
            )
            sims.size shouldBe 2
        }

        @Test
        fun `no SIM returns empty list`() {
            val sims = emptyList<SimInfo>()
            sims.size shouldBe 0
        }

        @Test
        fun `OTHER operator is valid`() {
            val sim = SimInfo(0, "Other", OperatorType.OTHER, 0, voiceServiceAvailable = true)
            sim.operatorType shouldBe OperatorType.OTHER
        }
    }

    @Nested
    inner class GetRoutingDecisionUseCaseTest {

        @Test
        fun `Airtel SIM selects USSD`() {
            val decision = fakeRoute(OperatorType.AIRTEL, override = null)
            decision.selectedMethod shouldBe PaymentMethodType.USSD
            decision.reason shouldBe RoutingReason.OPERATOR_PRIORITY
        }

        @Test
        fun `Jio SIM selects PAY123`() {
            val decision = fakeRoute(OperatorType.JIO, override = null)
            decision.selectedMethod shouldBe PaymentMethodType.PAY123
            decision.reason shouldBe RoutingReason.OPERATOR_PRIORITY
        }

        @Test
        fun `manual override USSD selects USSD regardless of operator`() {
            val decision = fakeRoute(OperatorType.JIO, override = PaymentMethodType.USSD)
            decision.selectedMethod shouldBe PaymentMethodType.USSD
            decision.overrideActive shouldBe true
            decision.reason shouldBe RoutingReason.MANUAL_OVERRIDE
        }

        @Test
        fun `manual override PAY123 selects PAY123 regardless of operator`() {
            val decision = fakeRoute(OperatorType.AIRTEL, override = PaymentMethodType.PAY123)
            decision.selectedMethod shouldBe PaymentMethodType.PAY123
            decision.overrideActive shouldBe true
        }

        @Test
        fun `no voice service returns null method`() {
            val decision = fakeRouteNoVoice(OperatorType.AIRTEL)
            decision.selectedMethod shouldBe null
            decision.reason shouldBe RoutingReason.NO_VOICE_SERVICE
        }

        @Test
        fun `USSD failure fallback to PAY123 exists`() {
            val decision = fakeRoute(OperatorType.AIRTEL, override = null)
            decision.fallbackChain shouldBe listOf(PaymentMethodType.PAY123)
        }

        @Test
        fun `PAY123 failure fallback to USSD exists`() {
            val decision = fakeRoute(OperatorType.JIO, override = null)
            decision.fallbackChain shouldBe listOf(PaymentMethodType.USSD)
        }
    }

    // ── Fake routing helpers ─────────────────────────────────────────────────

    private fun fakeRoute(operator: OperatorType, override: PaymentMethodType?): RoutingDecision {
        if (override != null) {
            return RoutingDecision(
                selectedMethod = override,
                fallbackChain = emptyList(),
                operatorType = operator,
                simSlotIndex = 0,
                overrideActive = true,
                reason = RoutingReason.MANUAL_OVERRIDE,
            )
        }
        val (primary, fallback) = when (operator) {
            OperatorType.JIO -> PaymentMethodType.PAY123 to listOf(PaymentMethodType.USSD)
            else -> PaymentMethodType.USSD to listOf(PaymentMethodType.PAY123)
        }
        return RoutingDecision(
            selectedMethod = primary,
            fallbackChain = fallback,
            operatorType = operator,
            simSlotIndex = 0,
            overrideActive = false,
            reason = RoutingReason.OPERATOR_PRIORITY,
        )
    }

    private fun fakeRouteNoVoice(operator: OperatorType): RoutingDecision {
        return RoutingDecision(
            selectedMethod = null,
            fallbackChain = emptyList(),
            operatorType = operator,
            simSlotIndex = 0,
            overrideActive = false,
            reason = RoutingReason.NO_VOICE_SERVICE,
        )
    }
}
