package com.offlinepay.feature.payment

import androidx.lifecycle.SavedStateHandle
import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.designsystem.components.SimDisplayInfo
import com.offlinepay.core.domain.error.DomainError
import com.offlinepay.core.domain.model.*
import com.offlinepay.core.domain.usecase.payment.GetRoutingDecisionUseCase
import com.offlinepay.core.domain.usecase.settings.GetSettingsUseCase
import com.offlinepay.core.domain.usecase.sim.DetectSimUseCase
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentConfirmationViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val detectSimUseCase: DetectSimUseCase = mockk()
    private val getRoutingDecisionUseCase: GetRoutingDecisionUseCase = mockk()
    private val getSettingsUseCase: GetSettingsUseCase = mockk()

    private val paymentParams = PaymentParams(
        upiId = "merchant@upi",
        payeeName = "Merchant Shop",
        amount = 10000, // ₹100.00
        qrType = QrType.DYNAMIC,
        merchantCategoryCode = "5411"
    )

    private lateinit var savedStateHandle: SavedStateHandle

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        savedStateHandle = SavedStateHandle(mapOf("paymentParams" to paymentParams))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): PaymentConfirmationViewModel {
        return PaymentConfirmationViewModel(
            detectSimUseCase = detectSimUseCase,
            getRoutingDecisionUseCase = getRoutingDecisionUseCase,
            getSettingsUseCase = getSettingsUseCase,
            savedStateHandle = savedStateHandle
        )
    }

    @Nested
    inner class Initialization {

        @Test
        fun `loading initial data with single SIM sets Ready state`() = runTest {
            val simInfo = SimInfo(0, OperatorType.AIRTEL, 0, voiceServiceAvailable = true)
            val settings = AppSettings()
            val decision = RoutingDecision(
                selectedMethod = PaymentMethodType.USSD,
                fallbackChain = listOf(PaymentMethodType.PAY123),
                operatorType = OperatorType.AIRTEL,
                simSlotIndex = 0,
                overrideActive = false,
                reason = RoutingReason.OPERATOR_PRIORITY
            )

            coEvery { detectSimUseCase() } returns AppResult.Success(listOf(simInfo))
            coEvery { getSettingsUseCase() } returns flowOf(settings)
            coEvery { getRoutingDecisionUseCase(simInfo, paymentParams, settings) } returns AppResult.Success(decision)

            val viewModel = createViewModel()

            // Run tasks pending on the dispatcher
            advanceUntilIdle()

            val state = viewModel.uiState.value
            state.shouldBeInstanceOf<PaymentConfirmationUiState.Ready>()
            state.paymentParams shouldBe paymentParams
            state.sims.size shouldBe 1
            state.sims[0].subscriptionId shouldBe 0
            state.sims[0].displayName shouldBe "SIM 1 (Airtel)"
            state.selectedSim?.subscriptionId shouldBe 0
            state.routingDecision shouldBe decision
            state.isStaticQr shouldBe false
            state.editableAmountStr shouldBe "100.00"
            state.amountError shouldBe null
        }

        @Test
        fun `loading initial data with static QR leaves amount empty`() = runTest {
            val staticParams = PaymentParams(
                upiId = "merchant@upi",
                payeeName = "Merchant Shop",
                amount = null, // static QR
                qrType = QrType.STATIC
            )
            savedStateHandle = SavedStateHandle(mapOf("paymentParams" to staticParams))

            val simInfo = SimInfo(0, OperatorType.AIRTEL, 0, voiceServiceAvailable = true)
            val settings = AppSettings()
            val decision = RoutingDecision(
                selectedMethod = PaymentMethodType.USSD,
                fallbackChain = emptyList(),
                operatorType = OperatorType.AIRTEL,
                simSlotIndex = 0,
                overrideActive = false,
                reason = RoutingReason.OPERATOR_PRIORITY
            )

            coEvery { detectSimUseCase() } returns AppResult.Success(listOf(simInfo))
            coEvery { getSettingsUseCase() } returns flowOf(settings)
            coEvery { getRoutingDecisionUseCase(simInfo, staticParams, settings) } returns AppResult.Success(decision)

            val viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            state.shouldBeInstanceOf<PaymentConfirmationUiState.Ready>()
            state.isStaticQr shouldBe true
            state.editableAmountStr shouldBe ""
            state.amountError shouldBe null
        }

        @Test
        fun `loading initial data with no SIM sets Error state`() = runTest {
            coEvery { detectSimUseCase() } returns AppResult.Failure(DomainError.PaymentError.NoSim)
            coEvery { getSettingsUseCase() } returns flowOf(AppSettings())

            val viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            state.shouldBeInstanceOf<PaymentConfirmationUiState.Error>()
            state.message shouldBe "No SIM detected. Please insert a SIM card and try again."
        }
    }

    @Nested
    inner class Actions {

        @Test
        fun `AmountChanged updates state and validates amount`() = runTest {
            val simInfo = SimInfo(0, OperatorType.AIRTEL, 0, voiceServiceAvailable = true)
            val settings = AppSettings()
            val decision = RoutingDecision(
                selectedMethod = PaymentMethodType.USSD,
                fallbackChain = emptyList(),
                operatorType = OperatorType.AIRTEL,
                simSlotIndex = 0,
                overrideActive = false,
                reason = RoutingReason.OPERATOR_PRIORITY
            )

            coEvery { detectSimUseCase() } returns AppResult.Success(listOf(simInfo))
            coEvery { getSettingsUseCase() } returns flowOf(settings)
            coEvery { getRoutingDecisionUseCase(simInfo, paymentParams, settings) } returns AppResult.Success(decision)

            val viewModel = createViewModel()
            advanceUntilIdle()

            // Change amount to 0
            viewModel.onAction(PaymentConfirmationAction.AmountChanged("0"))
            var state = viewModel.uiState.value as PaymentConfirmationUiState.Ready
            state.editableAmountStr shouldBe "0"
            state.amountError shouldBe "Amount must be greater than zero"

            // Change amount to exceed limit
            viewModel.onAction(PaymentConfirmationAction.AmountChanged("100001"))
            state = viewModel.uiState.value as PaymentConfirmationUiState.Ready
            state.amountError shouldBe "Amount exceeds ₹1,00,000 NPCI limit"

            // Valid amount
            viewModel.onAction(PaymentConfirmationAction.AmountChanged("500.50"))
            state = viewModel.uiState.value as PaymentConfirmationUiState.Ready
            state.amountError shouldBe null
        }

        @Test
        fun `SimSelected recomputes routing decision`() = runTest {
            val sim1 = SimInfo(0, OperatorType.AIRTEL, 0, voiceServiceAvailable = true)
            val sim2 = SimInfo(1, OperatorType.JIO, 1, voiceServiceAvailable = true)
            val settings = AppSettings()
            val decision1 = RoutingDecision(
                selectedMethod = PaymentMethodType.USSD,
                fallbackChain = emptyList(),
                operatorType = OperatorType.AIRTEL,
                simSlotIndex = 0,
                overrideActive = false,
                reason = RoutingReason.OPERATOR_PRIORITY
            )
            val decision2 = RoutingDecision(
                selectedMethod = PaymentMethodType.PAY123,
                fallbackChain = emptyList(),
                operatorType = OperatorType.JIO,
                simSlotIndex = 1,
                overrideActive = false,
                reason = RoutingReason.OPERATOR_PRIORITY
            )

            coEvery { detectSimUseCase() } returns AppResult.Success(listOf(sim1, sim2))
            coEvery { getSettingsUseCase() } returns flowOf(settings)
            coEvery { getRoutingDecisionUseCase(sim1, paymentParams, settings) } returns AppResult.Success(decision1)
            coEvery { getRoutingDecisionUseCase(sim2, paymentParams, settings) } returns AppResult.Success(decision2)

            val viewModel = createViewModel()
            advanceUntilIdle()

            // Sim 2 selected
            val sim2Display = SimDisplayInfo(1, "SIM 2 (Jio)", "Jio", true)
            viewModel.onAction(PaymentConfirmationAction.SimSelected(sim2Display))

            // Check that routing decision sets to null during compute
            val stateMiddle = viewModel.uiState.value as PaymentConfirmationUiState.Ready
            stateMiddle.selectedSim shouldBe sim2Display

            advanceUntilIdle()

            val stateFinal = viewModel.uiState.value as PaymentConfirmationUiState.Ready
            stateFinal.routingDecision shouldBe decision2
        }

        @Test
        fun `ConfirmPayment triggers NavigateToUssd event when selected method is USSD`() = runTest {
            val simInfo = SimInfo(0, OperatorType.AIRTEL, 0, voiceServiceAvailable = true)
            val settings = AppSettings()
            val decision = RoutingDecision(
                selectedMethod = PaymentMethodType.USSD,
                fallbackChain = emptyList(),
                operatorType = OperatorType.AIRTEL,
                simSlotIndex = 0,
                overrideActive = false,
                reason = RoutingReason.OPERATOR_PRIORITY
            )

            coEvery { detectSimUseCase() } returns AppResult.Success(listOf(simInfo))
            coEvery { getSettingsUseCase() } returns flowOf(settings)
            coEvery { getRoutingDecisionUseCase(simInfo, paymentParams, settings) } returns AppResult.Success(decision)

            val viewModel = createViewModel()
            advanceUntilIdle()

            // Collect events channel in a flow list
            val eventsReceived = mutableListOf<PaymentConfirmationEvent>()
            val job = launch {
                viewModel.events.collect { eventsReceived.add(it) }
            }

            viewModel.onAction(PaymentConfirmationAction.ConfirmPayment)
            advanceUntilIdle()

            eventsReceived.size shouldBe 1
            val event = eventsReceived[0]
            event.shouldBeInstanceOf<PaymentConfirmationEvent.NavigateToUssd>()
            event.subscriptionId shouldBe 0
            event.params.amount shouldBe 10000

            job.cancel()
        }

        @Test
        fun `ConfirmPayment triggers NavigateToPay123 event when selected method is PAY123`() = runTest {
            val simInfo = SimInfo(1, OperatorType.JIO, 1, voiceServiceAvailable = true)
            val settings = AppSettings()
            val decision = RoutingDecision(
                selectedMethod = PaymentMethodType.PAY123,
                fallbackChain = emptyList(),
                operatorType = OperatorType.JIO,
                simSlotIndex = 1,
                overrideActive = false,
                reason = RoutingReason.OPERATOR_PRIORITY
            )

            coEvery { detectSimUseCase() } returns AppResult.Success(listOf(simInfo))
            coEvery { getSettingsUseCase() } returns flowOf(settings)
            coEvery { getRoutingDecisionUseCase(simInfo, paymentParams, settings) } returns AppResult.Success(decision)

            val viewModel = createViewModel()
            advanceUntilIdle()

            val eventsReceived = mutableListOf<PaymentConfirmationEvent>()
            val job = launch {
                viewModel.events.collect { eventsReceived.add(it) }
            }

            viewModel.onAction(PaymentConfirmationAction.ConfirmPayment)
            advanceUntilIdle()

            eventsReceived.size shouldBe 1
            val event = eventsReceived[0]
            event.shouldBeInstanceOf<PaymentConfirmationEvent.NavigateToPay123>()
            event.subscriptionId shouldBe 1

            job.cancel()
        }

        @Test
        fun `ConfirmPayment fails on invalid amount and does not emit event`() = runTest {
            val simInfo = SimInfo(0, OperatorType.AIRTEL, 0, voiceServiceAvailable = true)
            val settings = AppSettings()
            val decision = RoutingDecision(
                selectedMethod = PaymentMethodType.USSD,
                fallbackChain = emptyList(),
                operatorType = OperatorType.AIRTEL,
                simSlotIndex = 0,
                overrideActive = false,
                reason = RoutingReason.OPERATOR_PRIORITY
            )

            coEvery { detectSimUseCase() } returns AppResult.Success(listOf(simInfo))
            coEvery { getSettingsUseCase() } returns flowOf(settings)
            coEvery { getRoutingDecisionUseCase(simInfo, paymentParams, settings) } returns AppResult.Success(decision)

            val viewModel = createViewModel()
            advanceUntilIdle()

            // Invalid amount string
            viewModel.onAction(PaymentConfirmationAction.AmountChanged("invalid"))
            advanceUntilIdle()

            val eventsReceived = mutableListOf<PaymentConfirmationEvent>()
            val job = launch {
                viewModel.events.collect { eventsReceived.add(it) }
            }

            viewModel.onAction(PaymentConfirmationAction.ConfirmPayment)
            advanceUntilIdle()

            eventsReceived.size shouldBe 0
            val state = viewModel.uiState.value as PaymentConfirmationUiState.Ready
            state.amountError shouldBe "Amount must be greater than zero"

            job.cancel()
        }
    }
}
