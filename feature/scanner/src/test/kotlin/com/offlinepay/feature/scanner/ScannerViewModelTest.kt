package com.offlinepay.feature.scanner

import com.offlinepay.core.domain.error.DomainError
import com.offlinepay.core.domain.model.PaymentParams
import com.offlinepay.core.domain.model.QrParseResult
import com.offlinepay.core.domain.model.QrType
import com.offlinepay.core.domain.usecase.qr.ParseQrCodeUseCase
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScannerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val parseQrCodeUseCase: ParseQrCodeUseCase = mockk()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ScannerViewModel {
        return ScannerViewModel(parseQrCodeUseCase)
    }

    @Nested
    inner class StateTransitions {

        @Test
        fun `initial state is Loading`() {
            val viewModel = createViewModel()
            viewModel.uiState.value.shouldBeInstanceOf<ScannerUiState.Loading>()
        }

        @Test
        fun `PermissionGranted action transitions state to Ready with camera permission true`() {
            val viewModel = createViewModel()
            viewModel.onAction(ScannerUiAction.PermissionGranted)

            val state = viewModel.uiState.value
            state.shouldBeInstanceOf<ScannerUiState.Ready>()
            state.isCameraPermissionGranted shouldBe true
            state.isTorchOn shouldBe false
        }

        @Test
        fun `PermissionDenied action transitions state to PermissionRequired`() {
            val viewModel = createViewModel()
            viewModel.onAction(ScannerUiAction.PermissionDenied)

            viewModel.uiState.value.shouldBeInstanceOf<ScannerUiState.PermissionRequired>()
        }

        @Test
        fun `ToggleTorch changes torch state in Ready state`() {
            val viewModel = createViewModel()
            viewModel.onAction(ScannerUiAction.PermissionGranted)

            viewModel.onAction(ScannerUiAction.ToggleTorch)
            var state = viewModel.uiState.value as ScannerUiState.Ready
            state.isTorchOn shouldBe true

            viewModel.onAction(ScannerUiAction.ToggleTorch)
            state = viewModel.uiState.value as ScannerUiState.Ready
            state.isTorchOn shouldBe false
        }

        @Test
        fun `RescanRequested resets ParseError to Ready`() {
            val viewModel = createViewModel()
            // Set state to ParseError
            viewModel.onAction(ScannerUiAction.PermissionDenied) // random transition first
            val errorState = ScannerUiState.ParseError("Invalid QR", "URL")
            // Manually trigger rescan when state is not ParseError shouldn't do much,
            // but we can simulate the flow:
            // Frame analysis fails -> state becomes ParseError -> RescanRequested -> state becomes Ready.
            coEvery { parseQrCodeUseCase(any()) } returns QrParseResult.Failure(
                DomainError.QrError.InvalidScheme("Not a UPI scheme"),
                "URL"
            )

            viewModel.onAction(ScannerUiAction.FrameAnalysed("http://example.com"))
            advanceUntilIdle()

            viewModel.uiState.value.shouldBeInstanceOf<ScannerUiState.ParseError>()

            viewModel.onAction(ScannerUiAction.RescanRequested)
            val state = viewModel.uiState.value
            state.shouldBeInstanceOf<ScannerUiState.Ready>()
            state.isCameraPermissionGranted shouldBe true
        }
    }

    @Nested
    inner class FrameProcessing {

        @Test
        fun `FrameAnalysed with valid UPI QR emits NavigateToPaymentConfirmation event`() = runTest {
            val params = PaymentParams(
                upiId = "payee@upi",
                payeeName = "Merchant",
                amount = 1500,
                qrType = QrType.DYNAMIC
            )
            coEvery { parseQrCodeUseCase("upi://pay?pa=payee@upi&pn=Merchant&am=15.00") } returns QrParseResult.Success(params)

            val viewModel = createViewModel()
            advanceUntilIdle()

            val eventsReceived = mutableListOf<ScannerUiEvent>()
            val job = launch {
                viewModel.events.collect { eventsReceived.add(it) }
            }

            viewModel.onAction(ScannerUiAction.FrameAnalysed("upi://pay?pa=payee@upi&pn=Merchant&am=15.00"))
            advanceUntilIdle()

            eventsReceived.size shouldBe 1
            val event = eventsReceived[0]
            event.shouldBeInstanceOf<ScannerUiEvent.NavigateToPaymentConfirmation>()
            event.paymentParams shouldBe params

            job.cancel()
        }

        @Test
        fun `FrameAnalysed with invalid QR sets state to ParseError`() = runTest {
            coEvery { parseQrCodeUseCase("invalid-content") } returns QrParseResult.Failure(
                DomainError.QrError.MissingUpiId,
                "Text"
            )

            val viewModel = createViewModel()
            advanceUntilIdle()

            val eventsReceived = mutableListOf<ScannerUiEvent>()
            val job = launch {
                viewModel.events.collect { eventsReceived.add(it) }
            }

            viewModel.onAction(ScannerUiAction.FrameAnalysed("invalid-content"))
            advanceUntilIdle()

            eventsReceived.size shouldBe 0
            val state = viewModel.uiState.value
            state.shouldBeInstanceOf<ScannerUiState.ParseError>()
            state.message shouldBe "MissingUpiId"
            state.detectedType shouldBe "Text"

            job.cancel()
        }
    }
}
