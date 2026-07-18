package com.offlinepay.feature.payment.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinepay.core.domain.model.PaymentMethodType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
// Enum for which fallback action to present
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Indicates which alternative payment method button to display on the failure screen.
 *
 * - [TRY_PAY123] is shown when the failure originated from a USSD attempt → user can try 123PAY.
 * - [TRY_USSD]   is shown when the failure originated from a 123PAY attempt → user can try USSD.
 * - [NONE]       is shown for errors unrelated to either specific method (e.g. NoSim).
 *
 * Design reference: Section 13.2 (Error → UI Mapping)
 */
enum class FallbackMethod {
    TRY_PAY123,
    TRY_USSD,
    NONE,
}

// ─────────────────────────────────────────────────────────────────────────────
// UI state
// ─────────────────────────────────────────────────────────────────────────────

/**
 * UI state for [PaymentFailureScreen].
 *
 * Design reference: Section 10.1 (payment_failure route), Section 13.2
 * Requirements: Req 5.6, Req 5.10, Req 6.7, Req 12.5
 */
sealed class PaymentFailureUiState {

    /** Transient loading state while deriving error details from SavedStateHandle. */
    data object Loading : PaymentFailureUiState()

    /**
     * All failure details are resolved and the screen can render.
     *
     * @param failureTitle      Short heading derived from [DomainError] type.
     * @param failureMessage    Descriptive user-facing message per Section 13.2 mapping.
     * @param fallbackMethod    Which alternative action button to show (try 123PAY / try USSD / none).
     * @param originMethod      The payment method that originally failed, used for analytics.
     */
    data class Ready(
        val failureTitle: String,
        val failureMessage: String,
        val fallbackMethod: FallbackMethod,
        val originMethod: PaymentMethodType?,
    ) : PaymentFailureUiState()

    /** Unrecoverable error — missing or unparse-able args (should not occur in production). */
    data class Error(val message: String) : PaymentFailureUiState()
}

// ─────────────────────────────────────────────────────────────────────────────
// One-shot events
// ─────────────────────────────────────────────────────────────────────────────

/**
 * One-shot navigation/action events emitted by [PaymentFailureViewModel].
 *
 * Delivered via a [Channel] to guarantee exactly-once delivery across recompositions.
 *
 * Design reference: Section 10.1, Section 13.2
 */
sealed class PaymentFailureEvent {
    /** User tapped "Retry" — navigate back to the same payment method. */
    data object Retry : PaymentFailureEvent()

    /** User tapped "Try 123PAY" — navigate to the 123PAY flow. */
    data object NavigateToPay123 : PaymentFailureEvent()

    /** User tapped "Try USSD" — navigate to the USSD flow. */
    data object NavigateToUssd : PaymentFailureEvent()
}

// ─────────────────────────────────────────────────────────────────────────────
// DomainError type keys (string constants matching SavedStateHandle keys)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * String keys used in [SavedStateHandle] to pass error information to this screen.
 *
 * The navigation caller serialises a [DomainError] as:
 * - `errorType`    — simple class name of the concrete [DomainError] subtype
 *                    (e.g. "UssdError.Timeout", "Pay123Error.CallFailed").
 * - `errorMessage` — optional raw message or sanitised payload for BankDeclined errors.
 *
 * This approach avoids Parcelable dependencies in `:core:domain` (which has no Android deps).
 */
object PaymentFailureArgs {
    const val ERROR_TYPE = "errorType"
    const val ERROR_MESSAGE = "errorMessage"
}

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

/**
 * ViewModel for [PaymentFailureScreen].
 *
 * Responsibilities:
 * - Read `errorType` and `errorMessage` from [SavedStateHandle].
 * - Derive the user-facing [failureTitle], [failureMessage], and [fallbackMethod]
 *   from the error type string using the Section 13.2 error → UI mapping table.
 * - Handle "Retry", "Try 123PAY", and "Try USSD" button actions by emitting one-shot
 *   [PaymentFailureEvent]s.
 *
 * Error-type → UI mapping (per task spec and Section 13.2):
 * | errorType string               | Title                   | Message                                                                             | Fallback           |
 * |--------------------------------|-------------------------|-------------------------------------------------------------------------------------|--------------------|
 * | UssdError.Timeout              | "USSD Timed Out"        | "USSD session timed out. Please try again or use 123PAY."                           | TRY_PAY123         |
 * | UssdError.NetworkError         | "Network Error"         | "No cellular service detected. Check your SIM and signal."                          | TRY_PAY123         |
 * | UssdError.PermissionDenied     | "Permission Required"   | "CALL_PHONE permission required. Please grant it in Settings."                      | TRY_PAY123         |
 * | Pay123Error.CallFailed         | "Call Failed"           | "Unable to initiate 123PAY call. Check your phone settings."                        | TRY_USSD           |
 * | Pay123Error.NoService          | "No Cellular Service"   | "No voice service available on the selected SIM."                                   | TRY_USSD           |
 * | PaymentError.InsufficientFunds | "Insufficient Funds"    | "Insufficient funds in your bank account."                                          | NONE               |
 * | (any other)                    | "Payment Failed"        | "Payment failed. Please try again."                                                 | NONE               |
 *
 * Design reference: Section 10.1 (payment_failure route), Section 13.2
 * Requirements: Req 5.6, Req 5.10, Req 6.7, Req 12.5
 *
 * @param savedStateHandle Provides `errorType` and `errorMessage` navigation arguments.
 */
@HiltViewModel
class PaymentFailureViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow<PaymentFailureUiState>(PaymentFailureUiState.Loading)
    val uiState: StateFlow<PaymentFailureUiState> = _uiState.asStateFlow()

    /** Buffered channel — events are not dropped across recompositions. */
    private val _events = Channel<PaymentFailureEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        deriveFailureState()
    }

    // ── State derivation ──────────────────────────────────────────────────────

    private fun deriveFailureState() {
        val errorType: String = savedStateHandle[PaymentFailureArgs.ERROR_TYPE] ?: ""
        val errorMessage: String = savedStateHandle[PaymentFailureArgs.ERROR_MESSAGE] ?: ""

        if (errorType.isBlank()) {
            _uiState.value = PaymentFailureUiState.Error(
                "Payment failure details are unavailable. Please check your transaction history.",
            )
            return
        }

        val (title, message, fallback, originMethod) = mapErrorToUi(errorType, errorMessage)

        _uiState.value = PaymentFailureUiState.Ready(
            failureTitle = title,
            failureMessage = message,
            fallbackMethod = fallback,
            originMethod = originMethod,
        )
    }

    // ── Action handling ───────────────────────────────────────────────────────

    /** User tapped "Retry". Re-attempt the same payment method. */
    fun onRetry() {
        viewModelScope.launch {
            _events.send(PaymentFailureEvent.Retry)
        }
    }

    /** User tapped "Try 123PAY". Initiate the 123PAY fallback flow. */
    fun onTryPay123() {
        viewModelScope.launch {
            _events.send(PaymentFailureEvent.NavigateToPay123)
        }
    }

    /** User tapped "Try USSD". Initiate the USSD fallback flow. */
    fun onTryUssd() {
        viewModelScope.launch {
            _events.send(PaymentFailureEvent.NavigateToUssd)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Error → UI mapping (Section 13.2 + task spec)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Maps a serialised [errorType] string and optional [errorMessage] to the four
     * display properties required by [PaymentFailureUiState.Ready].
     *
     * The [errorType] string is expected to match the simple class name of the
     * [DomainError] subtype as serialised by the navigation caller, e.g.:
     * - `"UssdError.Timeout"`, `"UssdError.NetworkError"`, `"UssdError.PermissionDenied"`
     * - `"Pay123Error.CallFailed"`, `"Pay123Error.NoService"`
     * - `"PaymentError.InsufficientFunds"` (if added in future)
     *
     * Unknown / unmapped types fall back to a generic "Payment Failed" state with no
     * secondary action.
     *
     * @return A [ErrorUiMapping] data class holding (title, message, fallback, originMethod).
     */
    private fun mapErrorToUi(
        errorType: String,
        errorMessage: String,
    ): ErrorUiMapping = when {
        errorType.contains("UssdError.Timeout", ignoreCase = true) -> ErrorUiMapping(
            title = "USSD Timed Out",
            message = "USSD session timed out. Please try again or use 123PAY.",
            fallback = FallbackMethod.TRY_PAY123,
            originMethod = PaymentMethodType.USSD,
        )

        errorType.contains("UssdError.NetworkError", ignoreCase = true) -> ErrorUiMapping(
            title = "Network Error",
            message = "No cellular service detected. Check your SIM and signal.",
            fallback = FallbackMethod.TRY_PAY123,
            originMethod = PaymentMethodType.USSD,
        )

        errorType.contains("UssdError.PermissionDenied", ignoreCase = true) -> ErrorUiMapping(
            title = "Permission Required",
            message = "CALL_PHONE permission required. Please grant it in Settings.",
            fallback = FallbackMethod.TRY_PAY123,
            originMethod = PaymentMethodType.USSD,
        )

        errorType.contains("UssdError.BankDeclined", ignoreCase = true) -> ErrorUiMapping(
            title = "Payment Declined",
            message = errorMessage.ifBlank { "The bank declined the payment. Please try again." },
            fallback = FallbackMethod.TRY_PAY123,
            originMethod = PaymentMethodType.USSD,
        )

        errorType.contains("UssdError.OperatorUnsupported", ignoreCase = true) -> ErrorUiMapping(
            title = "Operator Not Supported",
            message = "USSD payments are not supported by your operator.",
            fallback = FallbackMethod.TRY_PAY123,
            originMethod = PaymentMethodType.USSD,
        )

        errorType.contains("Pay123Error.CallFailed", ignoreCase = true) -> ErrorUiMapping(
            title = "Call Failed",
            message = "Unable to initiate 123PAY call. Check your phone settings.",
            fallback = FallbackMethod.TRY_USSD,
            originMethod = PaymentMethodType.PAY123,
        )

        errorType.contains("Pay123Error.NoService", ignoreCase = true) -> ErrorUiMapping(
            title = "No Cellular Service",
            message = "No voice service available on the selected SIM.",
            fallback = FallbackMethod.TRY_USSD,
            originMethod = PaymentMethodType.PAY123,
        )

        errorType.contains("PaymentError.NoSim", ignoreCase = true) -> ErrorUiMapping(
            title = "No SIM Card",
            message = "No SIM card detected. Please insert a SIM and try again.",
            fallback = FallbackMethod.NONE,
            originMethod = null,
        )

        errorType.contains("PaymentError.NoService", ignoreCase = true) -> ErrorUiMapping(
            title = "No Cellular Service",
            message = "No cellular signal on the selected SIM. Please move to an area with coverage.",
            fallback = FallbackMethod.NONE,
            originMethod = null,
        )

        errorType.contains("PaymentError.InsufficientFunds", ignoreCase = true) -> ErrorUiMapping(
            title = "Insufficient Funds",
            message = "Insufficient funds in your bank account.",
            fallback = FallbackMethod.NONE,
            originMethod = null,
        )

        errorType.contains("PaymentError.AllMethodsFailed", ignoreCase = true) -> ErrorUiMapping(
            title = "Payment Failed",
            message = "All payment methods failed. Please try again later.",
            fallback = FallbackMethod.NONE,
            originMethod = null,
        )

        // Generic fallback — covers all unmapped DomainError variants.
        else -> ErrorUiMapping(
            title = "Payment Failed",
            message = "Payment failed. Please try again.",
            fallback = FallbackMethod.NONE,
            originMethod = null,
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helper
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Internal value holder for the result of [mapErrorToUi].
     * Destructuring is used at the call site.
     */
    private data class ErrorUiMapping(
        val title: String,
        val message: String,
        val fallback: FallbackMethod,
        val originMethod: PaymentMethodType?,
    )
}
