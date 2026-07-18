package com.offlinepay.core.domain.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Unit tests for [AppSettings] defaults and [RoutingPriorityConfig].
 */
class AppSettingsTest {

    @Test
    fun `default AppSettings has English language`() {
        AppSettings().language shouldBe "en"
    }

    @Test
    fun `default AppSettings has SYSTEM theme`() {
        AppSettings().theme shouldBe AppTheme.SYSTEM
    }

    @Test
    fun `default AppSettings onboarding is not complete`() {
        AppSettings().isOnboardingComplete shouldBe false
    }

    @Test
    fun `default AppSettings has no manual override`() {
        AppSettings().manualPaymentMethodOverride shouldBe null
    }

    @Test
    fun `default AppSettings has no preferred SIM`() {
        AppSettings().preferredSimSlot shouldBe null
    }

    @Test
    fun `default RoutingPriorityConfig routes Airtel to USSD first`() {
        val config = RoutingPriorityConfig.default()
        config.airtelPriority.first() shouldBe PaymentMethodType.USSD
    }

    @Test
    fun `default RoutingPriorityConfig routes Jio to PAY123 first`() {
        val config = RoutingPriorityConfig.default()
        config.jioPriority.first() shouldBe PaymentMethodType.PAY123
    }

    @Test
    fun `default RoutingPriorityConfig routes Vi to USSD first`() {
        val config = RoutingPriorityConfig.default()
        config.viPriority.first() shouldBe PaymentMethodType.USSD
    }

    @Test
    fun `default RoutingPriorityConfig routes BSNL to USSD first`() {
        val config = RoutingPriorityConfig.default()
        config.bsnlPriority.first() shouldBe PaymentMethodType.USSD
    }

    @Test
    fun `priorityFor returns correct list for each operator`() {
        val config = RoutingPriorityConfig.default()
        with(RoutingPriorityConfig.Companion) {
            config.priorityFor(OperatorType.AIRTEL) shouldBe config.airtelPriority
            config.priorityFor(OperatorType.VI) shouldBe config.viPriority
            config.priorityFor(OperatorType.BSNL) shouldBe config.bsnlPriority
            config.priorityFor(OperatorType.JIO) shouldBe config.jioPriority
            config.priorityFor(OperatorType.OTHER) shouldBe config.otherPriority
        }
    }
}
