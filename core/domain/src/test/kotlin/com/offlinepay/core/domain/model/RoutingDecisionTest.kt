package com.offlinepay.core.domain.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Unit tests for [RoutingDecision] and [RoutingReason].
 */
class RoutingDecisionTest {

    @Test
    fun `RoutingDecision with USSD method has correct method type`() {
        val decision = RoutingDecision(
            selectedMethod = PaymentMethodType.USSD,
            reason = RoutingReason.OPERATOR_PRIORITY,
            operatorType = OperatorType.AIRTEL,
            simSlotIndex = 0,
        )
        decision.selectedMethod shouldBe PaymentMethodType.USSD
        decision.overrideActive shouldBe false
    }

    @Test
    fun `RoutingDecision with manual override has overrideActive true`() {
        val decision = RoutingDecision(
            selectedMethod = PaymentMethodType.PAY123,
            reason = RoutingReason.MANUAL_OVERRIDE,
            overrideActive = true,
            operatorType = OperatorType.AIRTEL,
            simSlotIndex = 0,
        )
        decision.overrideActive shouldBe true
        decision.reason shouldBe RoutingReason.MANUAL_OVERRIDE
    }

    @Test
    fun `RoutingDecision with no service has null selectedMethod`() {
        val decision = RoutingDecision(
            selectedMethod = null,
            reason = RoutingReason.NO_VOICE_SERVICE,
            operatorType = OperatorType.BSNL,
            simSlotIndex = 1,
        )
        decision.selectedMethod shouldBe null
    }

    @Test
    fun `RoutingDecision fallbackChain defaults to empty`() {
        val decision = RoutingDecision(
            selectedMethod = PaymentMethodType.USSD,
            reason = RoutingReason.OPERATOR_PRIORITY,
            operatorType = OperatorType.VI,
            simSlotIndex = 0,
        )
        decision.fallbackChain shouldBe emptyList()
    }

    @Test
    fun `RoutingDecision with fallback chain is preserved`() {
        val decision = RoutingDecision(
            selectedMethod = PaymentMethodType.USSD,
            fallbackChain = listOf(PaymentMethodType.PAY123),
            reason = RoutingReason.OPERATOR_PRIORITY,
            operatorType = OperatorType.AIRTEL,
            simSlotIndex = 0,
        )
        decision.fallbackChain shouldBe listOf(PaymentMethodType.PAY123)
    }
}
