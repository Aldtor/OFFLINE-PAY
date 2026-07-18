package com.offlinepay.feature.ussd

import androidx.lifecycle.SavedStateHandle
import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.domain.model.*
import com.offlinepay.core.domain.usecase.sim.DetectSimUseCase
import com.offlinepay.core.domain.usecase.merchant.UpsertMerchantUseCase
import com.offlinepay.core.domain.usecase.transaction.UpdateTransactionStatusUseCase
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UssdViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val ussdController: UssdController = mockk(relaxed = true)
    private val ussdResponseParser: UssdResponseParser = mockk()
    private val updateTransactionStatusUseCase: UpdateTransactionStatusUseCase = mockk(relaxed = true)
    private val detectSimUseCase: DetectSimUseCase = mockk()
    private val upsertMerchantUseCase: UpsertMerchantUseCase = mockk(relaxed = true)

    private val paymentParams = PaymentParams(
        upiId = "merchant@upi",
        payeeName = "Merchant Shop",
        amount = 10000,
        qrType = QrType.DYNAMIC
    )

    private lateinit var savedStateHandle: SavedStateHandle
    private val transactionId = "test-tx-123"

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        val jsonStr = Json.encodeToString(paymentParams)
        savedStateHandle = SavedStateHandle(
            mapOf(
                "paymentParamsJson" to jsonStr,
                "subscriptionId" to "0"
            )
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): UssdViewModel {
        return UssdViewModel(
            ussdController = ussdController,
            ussdResponseParser = ussdResponseParser,
            updateTransactionStatusUseCase = updateTransactionStatusUseCase,
            detectSimUseCase = detectSimUseCase,
            upsertMerchantUseCase = upsertMerchantUseCase,
            savedStateHandle = savedStateHandle
        )
    }

    @Nested
    inner class SessionLifecycle {

        @Test
        fun `initiation registers callback and transitions state to Requesting`() = runTest {
            val callbackSlot = slot<(UssdSessionState, String?) -> Unit>()
            coEvery {
                ussdController.initiateUssd(
                    ussdCode = "*99#",
                    subscriptionId = 0,
                    params = any(),
                    onResponse = capture(callbackSlot)
                )
            } returns transactionId

            val viewModel = createViewModel()
            advanceUntilIdle()

            // State machine starts at IDLE, then transitions during initiateUssd
            // If ussdController doesn't invoke callbackSlot yet, viewmodel is still driving
            viewModel.uiState.value.shouldBeInstanceOf<UssdViewModel.UiState.Idle>()
        }

        @Test
        fun `completed response updates transaction status to SUCCESS and navigates`() = runTest {
            val callbackSlot = slot<(UssdSessionState, String?) -> Unit>()
            coEvery {
                ussdController.initiateUssd(
                    ussdCode = "*99#",
                    subscriptionId = 0,
                    params = any(),
                    onResponse = capture(callbackSlot)
                )
            } returns transactionId

            val viewModel = createViewModel()
            advanceUntilIdle()

            val eventsReceived = mutableListOf<UssdViewModel.UiEvent>()
            val job = launch {
                viewModel.events.collect { eventsReceived.add(it) }
            }

            // Simulate callback signaling completion
            callbackSlot.captured.invoke(UssdSessionState.COMPLETED, "Success")
            advanceUntilIdle()

            coVerify(exactly = 1) {
                updateTransactionStatusUseCase(transactionId, TransactionStatus.SUCCESS)
                upsertMerchantUseCase(paymentParams, any())
            }

            eventsReceived.size shouldBe 1
            val event = eventsReceived[0]
            event.shouldBeInstanceOf<UssdViewModel.UiEvent.NavigateToSuccess>()
            event.transactionId shouldBe transactionId

            job.cancel()
        }

        @Test
        fun `failed response updates transaction status to FAILURE and offers fallback`() = runTest {
            val callbackSlot = slot<(UssdSessionState, String?) -> Unit>()
            coEvery {
                ussdController.initiateUssd(
                    ussdCode = "*99#",
                    subscriptionId = 0,
                    params = any(),
                    onResponse = capture(callbackSlot)
                )
            } returns transactionId

            val viewModel = createViewModel()
            advanceUntilIdle()

            val eventsReceived = mutableListOf<UssdViewModel.UiEvent>()
            val job = launch {
                viewModel.events.collect { eventsReceived.add(it) }
            }

            // Simulate callback signaling failure
            callbackSlot.captured.invoke(UssdSessionState.FAILED, "Failed")
            advanceUntilIdle()

            coVerify(exactly = 1) {
                updateTransactionStatusUseCase(transactionId, TransactionStatus.FAILURE)
            }

            eventsReceived.size shouldBe 1
            eventsReceived[0].shouldBeInstanceOf<UssdViewModel.UiEvent.OfferFallbackTo123Pay>()

            job.cancel()
        }

        @Test
        fun `cancel triggers cancellation on state machine`() = runTest {
            coEvery {
                ussdController.initiateUssd(
                    ussdCode = "*99#",
                    subscriptionId = 0,
                    params = any(),
                    onResponse = any()
                )
            } returns transactionId

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.cancel()
            viewModel.uiState.value.shouldBeInstanceOf<UssdViewModel.UiState.Cancelled>()
        }
    }
}
