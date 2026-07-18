package com.offlinepay.feature.history

import android.view.WindowManager.LayoutParams.FLAG_SECURE
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.domain.model.MerchantProfile
import com.offlinepay.core.domain.model.TransactionRecord
import com.offlinepay.core.domain.repository.MerchantRepository
import com.offlinepay.core.domain.usecase.transaction.DeleteTransactionUseCase
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
// UI state
// ─────────────────────────────────────────────────────────────────────────────

sealed class TransactionReceiptUiState {
    data object Loading : TransactionReceiptUiState()
    data class Ready(
        val transaction: TransactionRecord,
        val merchant: MerchantProfile?,
        val showDeleteConfirmation: Boolean = false,
    ) : TransactionReceiptUiState()
    data class Error(val message: String) : TransactionReceiptUiState()
}

// ─────────────────────────────────────────────────────────────────────────────
// One-shot events
// ─────────────────────────────────────────────────────────────────────────────

sealed class TransactionReceiptEvent {
    data class ShareReceipt(val transactionId: String) : TransactionReceiptEvent()
    data object NavigateBack : TransactionReceiptEvent()
    data class ShowError(val message: String) : TransactionReceiptEvent()
}

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

/**
 * ViewModel for the Transaction Receipt screen.
 *
 * Loads a [TransactionRecord] + associated [MerchantProfile] by transaction ID.
 * Handles share and delete actions.
 *
 * Design reference: Section 10.2 (TransactionReceiptScreen)
 * Requirements: Req 8.14, Req 8.18, Req 9.23
 */
@HiltViewModel
class TransactionReceiptViewModel @Inject constructor(
    private val getTransactionByIdUseCase: GetTransactionByIdUseCase,
    private val deleteTransactionUseCase: DeleteTransactionUseCase,
    private val merchantRepository: MerchantRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow<TransactionReceiptUiState>(TransactionReceiptUiState.Loading)
    val uiState: StateFlow<TransactionReceiptUiState> = _uiState.asStateFlow()

    private val _events = Channel<TransactionReceiptEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val transactionId: String
        get() = savedStateHandle.get<String>("transactionId").orEmpty()

    init {
        loadReceipt()
    }

    private fun loadReceipt() {
        viewModelScope.launch {
            val id = transactionId
            if (id.isBlank()) {
                _uiState.value = TransactionReceiptUiState.Error("Transaction ID is missing.")
                return@launch
            }

            when (val result = getTransactionByIdUseCase(id)) {
                is AppResult.Success -> {
                    val record = result.data
                    // Also load merchant profile
                    val merchantResult = merchantRepository.getMerchantByUpiId(record.payeeUpiId)
                    val merchant = when (merchantResult) {
                        is AppResult.Success -> merchantResult.data
                        else -> null
                    }
                    _uiState.value = TransactionReceiptUiState.Ready(
                        transaction = record,
                        merchant = merchant,
                    )
                }
                is AppResult.Failure -> {
                    _uiState.value = TransactionReceiptUiState.Error(
                        "Could not load transaction details.",
                    )
                }
                else -> Unit
            }
        }
    }

    fun onShareTapped() {
        viewModelScope.launch {
            _events.send(TransactionReceiptEvent.ShareReceipt(transactionId))
        }
    }

    fun onDeleteTapped() {
        val current = _uiState.value as? TransactionReceiptUiState.Ready ?: return
        _uiState.value = current.copy(showDeleteConfirmation = true)
    }

    fun onDeleteConfirmed() {
        viewModelScope.launch {
            when (deleteTransactionUseCase(transactionId)) {
                is AppResult.Success -> _events.send(TransactionReceiptEvent.NavigateBack)
                is AppResult.Failure -> _events.send(
                    TransactionReceiptEvent.ShowError("Failed to delete transaction."),
                )
                else -> Unit
            }
        }
    }

    fun onDeleteCancelled() {
        val current = _uiState.value as? TransactionReceiptUiState.Ready ?: return
        _uiState.value = current.copy(showDeleteConfirmation = false)
    }
}
