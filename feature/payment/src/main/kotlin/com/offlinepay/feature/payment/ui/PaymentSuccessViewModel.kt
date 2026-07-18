package com.offlinepay.feature.payment.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.domain.model.PaymentMethodType
import com.offlinepay.core.domain.model.TransactionRecord
import com.offlinepay.core.domain.usecase.transaction.GetTransactionByIdUseCase
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
 * UI state for [PaymentSuccessScreen].
 *
 * Design reference: Section 10.1 (payment_success route), Section 13.2
 * Requirements: Req 6.6 (123PAY success), Req 5.9 (USSD success), Req 12.5
 */
sealed class PaymentSuccessUiState {

    /** Loading while the transaction record is being fetched from the local store. */
    data object Loading : PaymentSuccessUiState()

    /**
     * Transaction data is available and the screen can render.
     *
     * @param transactionId  Full UUID of the transaction.
     * @param truncatedId    First 8 characters of the UUID + "…" for display.
     * @param merchantName   Display name of the merchant (payeeName or payeeUpiId as fallback).
     * @param amountPaise    Payment amount in paise for formatting.
     * @param paymentMethod  The offline method that completed the payment.
     */
    data class Success(
        val transactionId: String,
        val truncatedId: String,
        val merchantName: String,
        val amountPaise: Long,
        val paymentMethod: PaymentMethodType,
    ) : PaymentSuccessUiState()

    /** Unrecoverable error — record not found or database failure. */
    data class Error(val message: String) : PaymentSuccessUiState()
}

/**
 * One-shot navigation events emitted by [PaymentSuccessViewModel].
 *
 * Delivered via a [Channel] to ensure exactly-once delivery across recompositions.
 */
sealed class PaymentSuccessEvent {
    /** Navigate to the transaction receipt screen for [transactionId]. */
    data class NavigateToReceipt(val transactionId: String) : PaymentSuccessEvent()

    /** Navigate back to the scanner (Pay Again). */
    data object NavigateToScanner : PaymentSuccessEvent()
}

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

/**
 * ViewModel for [PaymentSuccessScreen].
 *
 * Responsibilities:
 * - Load the [TransactionRecord] for the given [transactionId] nav arg.
 * - Map the record into [PaymentSuccessUiState.Success].
 * - Handle "View Receipt" and "Pay Again" actions by emitting navigation events.
 *
 * The screen is shared between USSD and 123PAY outcomes — the payment method label
 * is derived from [TransactionRecord.paymentMethod] so no special-casing is needed.
 *
 * Design reference: Section 10.1 (payment_success route), Section 10.2
 * Requirements: Req 5.9, Req 6.6, Req 8.14, Req 9.23, Req 12.5
 *
 * @param getTransactionByIdUseCase Loads a single [TransactionRecord] by UUID.
 * @param savedStateHandle          Provides `transactionId` from the navigation back-stack.
 */
@HiltViewModel
class PaymentSuccessViewModel @Inject constructor(
    private val getTransactionByIdUseCase: GetTransactionByIdUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow<PaymentSuccessUiState>(PaymentSuccessUiState.Loading)
    val uiState: StateFlow<PaymentSuccessUiState> = _uiState.asStateFlow()

    /** Buffered channel — events are not dropped across recompositions. */
    private val _events = Channel<PaymentSuccessEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** The transaction UUID passed in as a navigation argument. */
    private val transactionId: String
        get() = savedStateHandle.get<String>("transactionId").orEmpty()

    init {
        loadTransaction()
    }

    private fun loadTransaction() {
        viewModelScope.launch {
            val id = transactionId
            if (id.isBlank()) {
                _uiState.value = PaymentSuccessUiState.Error(
                    "Transaction ID is missing. Cannot display payment result.",
                )
                return@launch
            }

            when (val result = getTransactionByIdUseCase(id)) {
                is AppResult.Success -> {
                    val record: TransactionRecord = result.data
                    _uiState.value = PaymentSuccessUiState.Success(
                        transactionId = record.id,
                        truncatedId = record.id.take(8) + "…",
                        merchantName = record.merchantName
                            ?: record.payeeName
                            ?: record.payeeUpiId,
                        amountPaise = record.amountPaise,
                        paymentMethod = record.paymentMethod,
                    )
                }

                is AppResult.Failure -> {
                    _uiState.value = PaymentSuccessUiState.Error(
                        "Could not load transaction details. Please check your history.",
                    )
                }

                is AppResult.Loading -> Unit // GetTransactionByIdUseCase is a one-shot suspend call
            }
        }
    }

    // ── Action handling ───────────────────────────────────────────────────────

    /** User tapped "View Receipt". Navigates to the transaction receipt screen. */
    fun onViewReceipt() {
        val current = _uiState.value as? PaymentSuccessUiState.Success ?: return
        viewModelScope.launch {
            _events.send(PaymentSuccessEvent.NavigateToReceipt(current.transactionId))
        }
    }

    /** User tapped "Pay Again". Navigates back to the scanner screen. */
    fun onPayAgain() {
        viewModelScope.launch {
            _events.send(PaymentSuccessEvent.NavigateToScanner)
        }
    }
}
