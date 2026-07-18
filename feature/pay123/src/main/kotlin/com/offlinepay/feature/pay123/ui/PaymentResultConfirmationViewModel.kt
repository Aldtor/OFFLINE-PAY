package com.offlinepay.feature.pay123.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.domain.model.TransactionRecord
import com.offlinepay.core.domain.model.TransactionStatus
import com.offlinepay.core.domain.usecase.transaction.GetTransactionByIdUseCase
import com.offlinepay.core.domain.usecase.transaction.UpdateTransactionStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
// UI contracts
// ─────────────────────────────────────────────────────────────────────────────

/**
 * UI state for [PaymentResultConfirmationScreen].
 *
 * This screen is displayed when the user dismisses the 123PAY confirmation prompt
 * without answering — leaving the transaction in [TransactionStatus.UNKNOWN] status.
 * The user can still retrospectively mark the transaction as succeeded or failed.
 *
 * Design reference: Section 10.1 (payment_result_confirmation route), Section 13.2
 * Requirements: Req 6.7, Req 6.8 (123PAY result handling), Req 12.5, Req 12.9
 */
sealed class PaymentResultConfirmationUiState {

    /** Loading while the [TransactionRecord] is fetched from the local store. */
    data object Loading : PaymentResultConfirmationUiState()

    /**
     * Transaction record loaded — screen can render.
     *
     * @param transactionId   Full UUID of the transaction.
     * @param truncatedId     First 8 characters + "…" for display.
     * @param merchantName    Display name of the merchant (payeeName or payeeUpiId fallback).
     * @param upiId           Payee UPI ID.
     * @param amountPaise     Payment amount in paise for [UpiAmountFormatter].
     * @param timestampMs     Epoch-milliseconds when the transaction was created.
     * @param isUpdating      `true` while a status-update operation is in progress;
     *                        disables both deferred-action buttons to prevent double-tap.
     */
    data class Ready(
        val transactionId: String,
        val truncatedId: String,
        val merchantName: String,
        val upiId: String,
        val amountPaise: Long,
        val timestampMs: Long,
        val isUpdating: Boolean = false,
    ) : PaymentResultConfirmationUiState()

    /**
     * Database or navigation error — record not found or update failed.
     *
     * @param message Human-readable message to display.
     */
    data class Error(val message: String) : PaymentResultConfirmationUiState()
}

/**
 * One-shot navigation / side-effect events emitted by [PaymentResultConfirmationViewModel].
 *
 * Delivered via a [Channel] so they survive recompositions without being replayed.
 *
 * Requirements: Req 6.7 (navigate to success), Req 6.8 (navigate to failure),
 *               Req 12.5 (link to receipt)
 */
sealed class PaymentResultConfirmationEvent {

    /**
     * User marked the transaction as succeeded.
     * Navigate to `payment_success/{transactionId}`.
     */
    data class NavigateToSuccess(val transactionId: String) : PaymentResultConfirmationEvent()

    /**
     * User marked the transaction as failed.
     * Navigate to `payment_failure`.
     */
    data object NavigateToFailure : PaymentResultConfirmationEvent()

    /**
     * User tapped "View Receipt".
     * Navigate to `transaction_receipt/{transactionId}`.
     */
    data class NavigateToReceipt(val transactionId: String) : PaymentResultConfirmationEvent()
}

/**
 * User-driven actions dispatched from [PaymentResultConfirmationScreen].
 */
sealed class PaymentResultConfirmationAction {

    /**
     * User decided the payment actually succeeded.
     * Updates status to [TransactionStatus.SUCCESS] and navigates to the success screen.
     *
     * Requirements: Req 6.7
     */
    data object MarkAsSucceeded : PaymentResultConfirmationAction()

    /**
     * User decided the payment actually failed.
     * Updates status to [TransactionStatus.FAILURE] and navigates to the failure screen.
     *
     * Requirements: Req 6.8
     */
    data object MarkAsFailed : PaymentResultConfirmationAction()

    /**
     * User tapped "View Receipt".
     * Navigates to the transaction receipt screen without changing status.
     *
     * Requirements: Req 12.5
     */
    data object ViewReceipt : PaymentResultConfirmationAction()
}

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

/**
 * ViewModel for the [PaymentResultConfirmationScreen].
 *
 * ## Responsibilities
 * - Load the [TransactionRecord] for the given `transactionId` nav argument.
 * - Map the record into [PaymentResultConfirmationUiState.Ready].
 * - Handle [PaymentResultConfirmationAction.MarkAsSucceeded] /
 *   [PaymentResultConfirmationAction.MarkAsFailed] by calling
 *   [UpdateTransactionStatusUseCase] then emitting the appropriate navigation event.
 * - Handle [PaymentResultConfirmationAction.ViewReceipt] by emitting
 *   [PaymentResultConfirmationEvent.NavigateToReceipt].
 * - Guard against double-tap via the `isUpdating` flag in [PaymentResultConfirmationUiState.Ready].
 *
 * ## Design rationale
 * The UNKNOWN status is a "deferred resolution" state: the user was on the 123PAY
 * IVR call but dismissed the result prompt without confirming. This screen allows
 * them to retrospectively mark the transaction correctly once they know the outcome
 * (e.g., by checking their bank balance or SMS). Per Req 6.7/6.8, the status update
 * must happen before navigation so the transaction record is consistent.
 *
 * Design reference: Section 10.1 (payment_result_confirmation route)
 * Requirements: Req 6.7, Req 6.8, Req 12.5, Req 12.9
 *
 * @param getTransactionByIdUseCase     Loads a single [TransactionRecord] by UUID.
 * @param updateTransactionStatusUseCase Updates the status of an existing transaction.
 * @param savedStateHandle               Provides `transactionId` from the nav back-stack.
 */
@HiltViewModel
class PaymentResultConfirmationViewModel @Inject constructor(
    private val getTransactionByIdUseCase: GetTransactionByIdUseCase,
    private val updateTransactionStatusUseCase: UpdateTransactionStatusUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<PaymentResultConfirmationUiState>(PaymentResultConfirmationUiState.Loading)
    val uiState: StateFlow<PaymentResultConfirmationUiState> = _uiState.asStateFlow()

    /** Buffered channel — events are not dropped across recompositions or during navigation. */
    private val _events = Channel<PaymentResultConfirmationEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** The transaction UUID passed in as a navigation argument. */
    private val transactionId: String
        get() = savedStateHandle.get<String>("transactionId").orEmpty()

    init {
        loadTransaction()
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Entry point for all user interactions from the screen composable.
     */
    fun onAction(action: PaymentResultConfirmationAction) {
        when (action) {
            is PaymentResultConfirmationAction.MarkAsSucceeded ->
                updateStatus(TransactionStatus.SUCCESS)

            is PaymentResultConfirmationAction.MarkAsFailed ->
                updateStatus(TransactionStatus.FAILURE)

            is PaymentResultConfirmationAction.ViewReceipt ->
                navigateToReceipt()
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun loadTransaction() {
        viewModelScope.launch {
            val id = transactionId
            if (id.isBlank()) {
                _uiState.value = PaymentResultConfirmationUiState.Error(
                    "Transaction ID is missing. Cannot display payment result.",
                )
                return@launch
            }

            when (val result = getTransactionByIdUseCase(id)) {
                is AppResult.Success -> {
                    val record: TransactionRecord = result.data
                    _uiState.value = PaymentResultConfirmationUiState.Ready(
                        transactionId = record.id,
                        truncatedId   = record.id.take(8) + "…",
                        merchantName  = record.merchantName
                            ?: record.payeeName
                            ?: record.payeeUpiId,
                        upiId         = record.payeeUpiId,
                        amountPaise   = record.amountPaise,
                        timestampMs   = record.timestampMs,
                    )
                }

                is AppResult.Failure -> {
                    _uiState.value = PaymentResultConfirmationUiState.Error(
                        "Could not load transaction details. Please check your history.",
                    )
                }

                is AppResult.Loading -> Unit
            }
        }
    }

    /**
     * Updates the transaction status to [newStatus], then emits the appropriate
     * navigation event.
     *
     * The `isUpdating` flag is set to `true` during the operation to disable
     * both buttons and prevent double-tap.
     *
     * Requirements: Req 6.7 (SUCCESS path), Req 6.8 (FAILURE path)
     */
    private fun updateStatus(newStatus: TransactionStatus) {
        val current = _uiState.value as? PaymentResultConfirmationUiState.Ready ?: return
        if (current.isUpdating) return // Guard against concurrent calls

        _uiState.value = current.copy(isUpdating = true)

        viewModelScope.launch {
            when (updateTransactionStatusUseCase(current.transactionId, newStatus)) {
                is AppResult.Success -> {
                    when (newStatus) {
                        TransactionStatus.SUCCESS ->
                            _events.send(
                                PaymentResultConfirmationEvent.NavigateToSuccess(
                                    current.transactionId,
                                ),
                            )

                        TransactionStatus.FAILURE ->
                            _events.send(PaymentResultConfirmationEvent.NavigateToFailure)

                        else -> {
                            // Should not happen — only SUCCESS and FAILURE are dispatched here
                            _uiState.value = current.copy(isUpdating = false)
                        }
                    }
                }

                is AppResult.Failure -> {
                    // Re-enable buttons and show error state
                    _uiState.value = PaymentResultConfirmationUiState.Error(
                        "Failed to update payment status. Please try again or check your history.",
                    )
                }

                is AppResult.Loading -> Unit
            }
        }
    }

    private fun navigateToReceipt() {
        val current = _uiState.value as? PaymentResultConfirmationUiState.Ready ?: return
        viewModelScope.launch {
            _events.send(
                PaymentResultConfirmationEvent.NavigateToReceipt(current.transactionId),
            )
        }
    }
}
