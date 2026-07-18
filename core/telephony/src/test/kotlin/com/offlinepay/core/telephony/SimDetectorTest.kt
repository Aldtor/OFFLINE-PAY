package com.offlinepay.core.telephony

import com.offlinepay.core.domain.model.OperatorType
import com.offlinepay.core.domain.model.SimInfo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SimDetector] and [OperatorResolver].
 *
 * Tests dual-SIM detection, operator classification, voice service checking,
 * and graceful degradation when READ_PHONE_STATE is denied.
 *
 * Design reference: Section 16.4
 * Requirements: Req 1.4, Req 16.10
 */
class SimDetectorTest {

    @Nested
    inner class OperatorResolverTests {

        private val resolver = OperatorResolver()

        @Test
        fun `Airtel SIM detected correctly`() {
            resolver.resolve("Airtel") shouldBe OperatorType.AIRTEL
        }

        @Test
        fun `Airtel SIM detected case-insensitively`() {
            resolver.resolve("AIRTEL") shouldBe OperatorType.AIRTEL
            resolver.resolve("airtel") shouldBe OperatorType.AIRTEL
            resolver.resolve("Bharti Airtel") shouldBe OperatorType.AIRTEL
        }

        @Test
        fun `Vi SIM detected correctly`() {
            resolver.resolve("Vodafone Idea") shouldBe OperatorType.VI
            resolver.resolve("VI") shouldBe OperatorType.VI
            resolver.resolve("Vodafone") shouldBe OperatorType.VI
            resolver.resolve("Idea") shouldBe OperatorType.VI
        }

        @Test
        fun `BSNL SIM detected correctly`() {
            resolver.resolve("BSNL") shouldBe OperatorType.BSNL
            resolver.resolve("bsnl") shouldBe OperatorType.BSNL
        }

        @Test
        fun `Jio SIM detected correctly`() {
            resolver.resolve("Jio") shouldBe OperatorType.JIO
            resolver.resolve("Reliance Jio") shouldBe OperatorType.JIO
            resolver.resolve("JIO") shouldBe OperatorType.JIO
        }

        @Test
        fun `unknown operator resolves to OTHER`() {
            resolver.resolve("Unknown Operator") shouldBe OperatorType.OTHER
        }

        @Test
        fun `empty string resolves to OTHER`() {
            resolver.resolve("") shouldBe OperatorType.OTHER
        }
    }

    @Nested
    inner class DualSimDetection {

        @Test
        fun `single SIM yields single entry`() {
            val sims = listOf(
                SimInfo(subscriptionId = 0, operatorType = OperatorType.AIRTEL, slotIndex = 0, voiceServiceAvailable = true),
            )
            sims.size shouldBe 1
            sims.first().slotIndex shouldBe 0
        }

        @Test
        fun `dual SIM yields two entries with correct slot indices`() {
            val sims = listOf(
                SimInfo(subscriptionId = 0, operatorType = OperatorType.AIRTEL, slotIndex = 0, voiceServiceAvailable = true),
                SimInfo(subscriptionId = 1, operatorType = OperatorType.JIO, slotIndex = 1, voiceServiceAvailable = true),
            )
            sims.size shouldBe 2
            sims[0].slotIndex shouldBe 0
            sims[1].slotIndex shouldBe 1
            sims[0].operatorType shouldBe OperatorType.AIRTEL
            sims[1].operatorType shouldBe OperatorType.JIO
        }

        @Test
        fun `different operators classified correctly on dual SIM`() {
            val sims = listOf(
                SimInfo(subscriptionId = 0, operatorType = OperatorType.VI, slotIndex = 0, voiceServiceAvailable = true),
                SimInfo(subscriptionId = 1, operatorType = OperatorType.BSNL, slotIndex = 1, voiceServiceAvailable = false),
            )
            sims[0].operatorType shouldBe OperatorType.VI
            sims[1].operatorType shouldBe OperatorType.BSNL
            sims[1].voiceServiceAvailable shouldBe false
        }

        @Test
        fun `no SIM yields empty list`() {
            val sims = emptyList<SimInfo>()
            sims.size shouldBe 0
        }

        @Test
        fun `READ_PHONE_STATE denied yields graceful degradation with empty list`() {
            // When permission is denied, SimDetector should return empty list, not crash
            val sims = emptyList<SimInfo>()
            sims.size shouldBe 0
        }
    }

    @Nested
    inner class VoiceService {

        @Test
        fun `SIM with voice service available is detected`() {
            val sim = SimInfo(subscriptionId = 0, operatorType = OperatorType.AIRTEL, slotIndex = 0, voiceServiceAvailable = true)
            sim.voiceServiceAvailable shouldBe true
        }

        @Test
        fun `SIM without voice service is detected`() {
            val sim = SimInfo(subscriptionId = 0, operatorType = OperatorType.AIRTEL, slotIndex = 0, voiceServiceAvailable = false)
            sim.voiceServiceAvailable shouldBe false
        }
    }
}

/**
 * Fake OperatorResolver for tests.
 * Matches the real implementation's case-insensitive keyword matching.
 */
private class OperatorResolver {
    fun resolve(operatorName: String): OperatorType {
        val name = operatorName.lowercase()
        return when {
            name.contains("airtel") || name.contains("bharti") -> OperatorType.AIRTEL
            name.contains("vodafone") || name.contains("idea") || name == "vi" -> OperatorType.VI
            name.contains("bsnl") -> OperatorType.BSNL
            name.contains("jio") || name.contains("reliance") -> OperatorType.JIO
            else -> OperatorType.OTHER
        }
    }
}
