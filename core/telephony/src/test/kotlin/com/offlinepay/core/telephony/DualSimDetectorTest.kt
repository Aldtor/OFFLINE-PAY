package com.offlinepay.core.telephony

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.domain.error.DomainError
import com.offlinepay.core.domain.model.OperatorType
import com.offlinepay.core.domain.model.SimInfo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Dual-SIM detection tests for [SimDetector].
 *
 * Mocks [SubscriptionManager] to simulate dual-SIM scenarios.
 * Verifies correct operator classification, slot indexing, and graceful degradation.
 *
 * Design reference: Section 16.4
 * Requirements: Req 1.4, Req 3.1–3.7, Req 16.10
 */
class DualSimDetectorTest {

    private val context: Context = mockk(relaxed = true)
    private val operatorResolver = OperatorResolver()
    private val voiceServiceChecker: VoiceServiceChecker = mockk()
    private val subscriptionManager: SubscriptionManager = mockk()

    @BeforeEach
    fun setup() {
        mockkStatic(ContextCompat::class)
        every {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
        } returns PackageManager.PERMISSION_GRANTED
        every {
            context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
        } returns subscriptionManager
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(ContextCompat::class)
    }

    private fun createDetector() = SimDetector(context, operatorResolver, voiceServiceChecker)

    private fun mockSubscription(subscriptionId: Int, carrierName: String, slotIndex: Int): SubscriptionInfo {
        val info = mockk<SubscriptionInfo>()
        every { info.subscriptionId } returns subscriptionId
        every { info.carrierName } returns carrierName
        every { info.simSlotIndex } returns slotIndex
        return info
    }

    @Nested
    inner class DualSimDetection {

        @Test
        fun `two active SIMs detected with correct slot indices`() {
            val airtelSub = mockSubscription(1, "Airtel", 0)
            val jioSub = mockSubscription(2, "Reliance Jio", 1)
            every { subscriptionManager.activeSubscriptionInfoList } returns listOf(airtelSub, jioSub)
            every { voiceServiceChecker.isVoiceServiceAvailable(1) } returns true
            every { voiceServiceChecker.isVoiceServiceAvailable(2) } returns true

            val result = createDetector().detectSims()

            result.shouldBeInstanceOf<AppResult.Success<List<SimInfo>>>()
            val sims = (result as AppResult.Success).data
            sims.size shouldBe 2
            sims[0].subscriptionId shouldBe 1
            sims[0].operatorType shouldBe OperatorType.AIRTEL
            sims[0].slotIndex shouldBe 0
            sims[1].subscriptionId shouldBe 2
            sims[1].operatorType shouldBe OperatorType.JIO
            sims[1].slotIndex shouldBe 1
        }

        @Test
        fun `different operators classified correctly in dual-SIM`() {
            val viSub = mockSubscription(10, "Vodafone Idea", 0)
            val bsnlSub = mockSubscription(11, "BSNL", 1)
            every { subscriptionManager.activeSubscriptionInfoList } returns listOf(viSub, bsnlSub)
            every { voiceServiceChecker.isVoiceServiceAvailable(any()) } returns true

            val result = createDetector().detectSims()

            val sims = (result as AppResult.Success).data
            sims[0].operatorType shouldBe OperatorType.VI
            sims[1].operatorType shouldBe OperatorType.BSNL
        }
    }

    @Nested
    inner class PermissionDenied {

        @Test
        fun `READ_PHONE_STATE denied returns graceful failure with empty list`() {
            every {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            } returns PackageManager.PERMISSION_DENIED

            val result = createDetector().detectSims()

            result.shouldBeInstanceOf<AppResult.Failure<DomainError>>()
            val failure = result as AppResult.Failure
            failure.error.shouldBeInstanceOf<DomainError.PermissionError.ReadPhoneStateRequired>()
        }
    }

    @Nested
    inner class NoSim {

        @Test
        fun `empty subscription list returns NoSim failure`() {
            every { subscriptionManager.activeSubscriptionInfoList } returns emptyList()

            val result = createDetector().detectSims()

            result.shouldBeInstanceOf<AppResult.Failure<DomainError>>()
            (result as AppResult.Failure).error shouldBe DomainError.PaymentError.NoSim
        }

        @Test
        fun `null subscription list returns NoSim failure`() {
            every { subscriptionManager.activeSubscriptionInfoList } returns null

            val result = createDetector().detectSims()

            result.shouldBeInstanceOf<AppResult.Failure<DomainError>>()
            (result as AppResult.Failure).error shouldBe DomainError.PaymentError.NoSim
        }
    }
}
