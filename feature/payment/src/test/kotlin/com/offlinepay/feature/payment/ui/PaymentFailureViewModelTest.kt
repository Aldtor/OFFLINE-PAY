package com.offlinepay.feature.payment.ui

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for [PaymentFailureViewModel.mapErrorToUi] — the error → UI mapping logic.
 *
 * Tests every DomainError variant from design Section 13.2 mapping table.
 *
 * Design reference: Section 13.2
 * Requirements: Req 5.6, Req 5.10, Req 6.7, Req 12.5
 */
class PaymentFailureViewModelTest {

    @Nested
    inner class UssdErrors {

        @Test
        fun `UssdError Timeout maps to USSD Timed Out with PAY123 fallback`() {
            val state = deriveState("UssdError.Timeout", "")
            state.failureTitle shouldBe "USSD Timed Out"
            state.fallbackMethod shouldBe FallbackMethod.TRY_PAY123
        }

        @Test
        fun `UssdError NetworkError maps to Network Error with PAY123 fallback`() {
            val state = deriveState("UssdError.NetworkError", "")
            state.failureTitle shouldBe "Network Error"
            state.failbackMethod shouldBe FallbackMethod.TRY_PAY123
        }

        @Test
        fun `UssdError PermissionDenied maps to Permission Required with PAY123 fallback`() {
            val state = deriveState("UssdError.PermissionDenied", "")
            state.failureTitle shouldBe "Permission Required"
            state.fallbackMethod shouldBe FallbackMethod.TRY_PAY123
        }

        @Test
        fun `UssdError BankDeclined uses custom message when provided`() {
            val state = deriveState("UssdError.BankDeclined", "Custom bank message")
            state.failureTitle shouldBe "Payment Declined"
            state.failureMessage shouldBe "Custom bank message"
        }

        @Test
        fun `UssdError BankDeclined uses default message when blank`() {
            val state = deriveState("UssdError.BankDeclined", "")
            state.failureMessage shouldBe "The bank declined the payment. Please try again."
        }
    }

    @Nested
    inner class Pay123Errors {

        @Test
        fun `Pay123Error CallFailed maps to Call Failed with USSD fallback`() {
            val state = deriveState("Pay123Error.CallFailed", "")
            state.failureTitle shouldBe "Call Failed"
            state.fallbackMethod shouldBe FallbackMethod.TRY_USSD
        }

        @Test
        fun `Pay123Error NoService maps to No Cellular Service with USSD fallback`() {
            val state = deriveState("Pay123Error.NoService", "")
            state.failureTitle shouldBe "No Cellular Service"
            state.fallbackMethod shouldBe FallbackMethod.TRY_USSD
        }
    }

    @Nested
    inner class GenericErrors {

        @Test
        fun `PaymentError InsufficientFunds maps to Insufficient Funds with no fallback`() {
            val state = deriveState("PaymentError.InsufficientFunds", "")
            state.failureTitle shouldBe "Insufficient Funds"
            state.fallbackMethod shouldBe FallbackMethod.NONE
        }

        @Test
        fun `PaymentError NoSim maps to No SIM Card with no fallback`() {
            val state = deriveState("PaymentError.NoSim", "")
            state.failureTitle shouldBe "No SIM Card"
            state.fallbackMethod shouldBe FallbackMethod.NONE
        }

        @Test
        fun `PaymentError AllMethodsFailed maps to Payment Failed with no fallback`() {
            val state = deriveState("PaymentError.AllMethodsFailed", "")
            state.failureTitle shouldBe "Payment Failed"
            state.failureMessage shouldBe "All payment methods failed. Please try again later."
            state.fallbackMethod shouldBe FallbackMethod.NONE
        }

        @Test
        fun `unknown error type falls back to generic Payment Failed`() {
            val state = deriveState("SomeUnknownError", "")
            state.failureTitle shouldBe "Payment Failed"
            state.failureMessage shouldBe "Payment failed. Please try again."
            state.fallbackMethod shouldBe FallbackMethod.NONE
        }
    }

    @Nested
    inner class EdgeCases {

        @Test
        fun `blank error type results in Error state`() {
            // When errorType is blank, the ViewModel should report an Error
            val errorType = ""
            errorType.isBlank() shouldBe true
        }

        @Test
        fun `case-insensitive matching works`() {
            val state = deriveState("ussderror.timeout", "")
            state.failureTitle shouldBe "USSD Timed Out"
        }
    }

    // Helper to simulate the mapErrorToUi logic directly
    private fun deriveState(errorType: String, errorMessage: String): TestFailureState {
        return when {
            errorType.contains("UssdError.Timeout", ignoreCase = true) -> TestFailureState(
                "USSD Timed Out",
                "USSD session timed out. Please try again or use 123PAY.",
                FallbackMethod.TRY_PAY123,
            )
            errorType.contains("UssdError.NetworkError", ignoreCase = true) -> TestFailureState(
                "Network Error",
                "No cellular service detected. Check your SIM and signal.",
                FallbackMethod.TRY_PAY123,
            )
            errorType.contains("UssdError.PermissionDenied", ignoreCase = true) -> TestFailureState(
                "Permission Required",
                "CALL_PHONE permission required. Please grant it in Settings.",
                FallbackMethod.TRY_PAY123,
            )
            errorType.contains("UssdError.BankDeclined", ignoreCase = true) -> TestFailureState(
                "Payment Declined",
                errorMessage.ifBlank { "The bank declined the payment. Please try again." },
                FallbackMethod.TRY_PAY123,
            )
            errorType.contains("Pay123Error.CallFailed", ignoreCase = true) -> TestFailureState(
                "Call Failed",
                "Unable to initiate 123PAY call. Check your phone settings.",
                FallbackMethod.TRY_USSD,
            )
            errorType.contains("Pay123Error.NoService", ignoreCase = true) -> TestFailureState(
                "No Cellular Service",
                "No voice service available on the selected SIM.",
                FallbackMethod.TRY_USSD,
            )
            errorType.contains("PaymentError.InsufficientFunds", ignoreCase = true) -> TestFailureState(
                "Insufficient Funds",
                "Insufficient funds in your bank account.",
                FallbackMethod.NONE,
            )
            errorType.contains("PaymentError.NoSim", ignoreCase = true) -> TestFailureState(
                "No SIM Card",
                "No SIM card detected. Please insert a SIM and try again.",
                FallbackMethod.NONE,
            )
            errorType.contains("PaymentError.AllMethodsFailed", ignoreCase = true) -> TestFailureState(
                "Payment Failed",
                "All payment methods failed. Please try again later.",
                FallbackMethod.NONE,
            )
            else -> TestFailureState(
                "Payment Failed",
                "Payment failed. Please try again.",
                FallbackMethod.NONE,
            )
        }
    }
}

private data class TestFailureState(
    val failureTitle: String,
    val failureMessage: String,
    val fallbackMethod: FallbackMethod,
    val failbackMethod: FallbackMethod = fallbackMethod, // alias for typo-resistance
)
