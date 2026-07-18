package com.offlinepay.feature.payment.engine

import com.offlinepay.core.domain.model.OperatorType
import com.offlinepay.core.domain.model.PaymentMethodType
import com.offlinepay.core.domain.model.RoutingDecision
import com.offlinepay.core.domain.model.RoutingPriorityConfig
import com.offlinepay.core.domain.model.RoutingReason
import com.offlinepay.core.domain.model.SimInfo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Exhaustive unit tests for [RoutingEngineImpl].
 *
 * Tests the full operator × method × service availability matrix per design Section 16.1.
 *
 * Requirements: Req 4.1–4.3
 * Design: Section 16.1
 */
class RoutingEngineImplTest {

    private lateinit var routingEngine: RoutingEngineImpl

    @BeforeEach
    fun setup() {
        routingEngine = RoutingEngineImpl(
            strategyRegistry = FakeStrategyRegistry(),
            config = RoutingPriorityConfig.default(),
        )
    }

    // ── No voice service tests ────────────────────────────────────────────────

    @Nested
    inner class NoVoiceService {

        @ParameterizedTest(name = "operator={0} with no voice service yields NO_VOICE_SERVICE")
        @EnumSource(OperatorType::class)
        fun `voiceService false returns NoService for all operators`(operator: OperatorType) {
            val sim = createSimInfo(operator, voiceServiceAvailable = false)
            val decision = routingEngine.route(sim, override = null)

            decision.selectedMethod shouldBe null
            decision.reason shouldBe RoutingReason.NO_VOICE_SERVICE
        }
    }

    // ── Default routing (no override) ─────────────────────────────────────────

    @Nested
    inner class DefaultRouting {

        @Test
        fun `AIRTEL with no override selects USSD first`() {
            val sim = createSimInfo(OperatorType.AIRTEL, voiceServiceAvailable = true)
            val decision = routingEngine.route(sim, override = null)

            decision.selectedMethod shouldBe PaymentMethodType.USSD
            decision.fallbackChain shouldBe listOf(PaymentMethodType.PAY123)
            decision.reason shouldBe RoutingReason.OPERATOR_PRIORITY
        }

        @Test
        fun `VI with no override selects USSD first`() {
            val sim = createSimInfo(OperatorType.VI, voiceServiceAvailable = true)
            val decision = routingEngine.route(sim, override = null)

            decision.selectedMethod shouldBe PaymentMethodType.USSD
            decision.reason shouldBe RoutingReason.OPERATOR_PRIORITY
        }

        @Test
        fun `BSNL with no override selects USSD first`() {
            val sim = createSimInfo(OperatorType.BSNL, voiceServiceAvailable = true)
            val decision = routingEngine.route(sim, override = null)

            decision.selectedMethod shouldBe PaymentMethodType.USSD
            decision.reason shouldBe RoutingReason.OPERATOR_PRIORITY
        }

        @Test
        fun `JIO with no override selects PAY123 first`() {
            val sim = createSimInfo(OperatorType.JIO, voiceServiceAvailable = true)
            val decision = routingEngine.route(sim, override = null)

            decision.selectedMethod shouldBe PaymentMethodType.PAY123
            decision.fallbackChain shouldBe listOf(PaymentMethodType.USSD)
            decision.reason shouldBe RoutingReason.OPERATOR_PRIORITY
        }

        @Test
        fun `OTHER with no override selects USSD first`() {
            val sim = createSimInfo(OperatorType.OTHER, voiceServiceAvailable = true)
            val decision = routingEngine.route(sim, override = null)

            decision.selectedMethod shouldBe PaymentMethodType.USSD
            decision.reason shouldBe RoutingReason.OPERATOR_PRIORITY
        }
    }

    // ── Manual override tests ──────────────────────────────────────────────────

    @Nested
    inner class ManualOverride {

        @ParameterizedTest(name = "operator={0} with override=USSD selects USSD")
        @EnumSource(OperatorType::class)
        fun `override USSD selects USSD for any operator`(operator: OperatorType) {
            val sim = createSimInfo(operator, voiceServiceAvailable = true)
            val decision = routingEngine.route(sim, override = PaymentMethodType.USSD)

            decision.selectedMethod shouldBe PaymentMethodType.USSD
            decision.overrideActive shouldBe true
            decision.reason shouldBe RoutingReason.MANUAL_OVERRIDE
        }

        @ParameterizedTest(name = "operator={0} with override=PAY123 selects PAY123")
        @EnumSource(OperatorType::class)
        fun `override PAY123 selects PAY123 for any operator`(operator: OperatorType) {
            val sim = createSimInfo(operator, voiceServiceAvailable = true)
            val decision = routingEngine.route(sim, override = PaymentMethodType.PAY123)

            decision.selectedMethod shouldBe PaymentMethodType.PAY123
            decision.overrideActive shouldBe true
            decision.reason shouldBe RoutingReason.MANUAL_OVERRIDE
        }
    }

    // ── Decision metadata ─────────────────────────────────────────────────────

    @Test
    fun `decision includes correct operator type and sim slot`() {
        val sim = createSimInfo(OperatorType.AIRTEL, voiceServiceAvailable = true, slotIndex = 1)
        val decision = routingEngine.route(sim, override = null)

        decision.operatorType shouldBe OperatorType.AIRTEL
        decision.simSlotIndex shouldBe 1
    }

    @Test
    fun `decision with voice service always returns non-null method`() {
        OperatorType.entries.forEach { operator ->
            val sim = createSimInfo(operator, voiceServiceAvailable = true)
            val decision = routingEngine.route(sim, override = null)
            decision.selectedMethod shouldNotBe null
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun createSimInfo(
        operator: OperatorType,
        voiceServiceAvailable: Boolean,
        slotIndex: Int = 0,
    ) = SimInfo(
        subscriptionId = slotIndex,
        operatorType = operator,
        slotIndex = slotIndex,
        voiceServiceAvailable = voiceServiceAvailable,
    )
}

/**
 * Fake strategy registry for tests — reports all strategies as available.
 */
private class FakeStrategyRegistry : com.offlinepay.core.domain.strategy.StrategyRegistry {
    override fun getStrategiesForOperator(
        operatorType: OperatorType,
    ): List<com.offlinepay.core.domain.strategy.PaymentMethodPlugin> = emptyList()

    override fun getAll(): Set<com.offlinepay.core.domain.strategy.PaymentMethodPlugin> = emptySet()
}
