package com.offlinepay.feature.merchant

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.domain.model.MerchantProfile
import com.offlinepay.core.domain.model.TransactionFilters
import com.offlinepay.core.domain.model.TransactionRecord
import com.offlinepay.core.domain.repository.MerchantRepository
import com.offlinepay.core.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
// UI state
// ─────────────────────────────────────────────────────────────────────────────

sealed class MerchantProfileUiState {
    data object Loading : MerchantProfileUiState()
    data class Ready(
        val merchant: MerchantProfile,
        val transactions: List<TransactionRecord> = emptyList(),
    ) : MerchantProfileUiState()
    data class Error(val message: String) : MerchantProfileUiState()
}

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

/**
 * ViewModel for the Merchant Profile screen.
 *
 * Loads a [MerchantProfile] + filtered [TransactionRecord] list for that merchant.
 * Handles favourite toggle action.
 *
 * Design reference: Section 3.10 (Merchant Profile)
 * Requirements: Req 18.1–18.7
 */
@HiltViewModel
class MerchantViewModel @Inject constructor(
    private val merchantRepository: MerchantRepository,
    private val transactionRepository: TransactionRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow<MerchantProfileUiState>(MerchantProfileUiState.Loading)
    val uiState: StateFlow<MerchantProfileUiState> = _uiState.asStateFlow()

    private val merchantUpiId: String
        get() = savedStateHandle.get<String>("upiId").orEmpty()

    /**
     * Latest known merchant, kept separately from the immutable snapshot captured in
     * [loadMerchant]'s collector so that [onToggleFavourite] updates are not clobbered
     * by the next emission of the (never-completing) transactions flow.
     */
    private var currentMerchant: MerchantProfile? = null

    init {
        loadMerchant()
    }

    private fun loadMerchant() {
        viewModelScope.launch {
            val upiId = merchantUpiId
            if (upiId.isBlank()) {
                _uiState.value = MerchantProfileUiState.Error("Merchant ID is missing.")
                return@launch
            }

            when (val result = merchantRepository.getMerchantByUpiId(upiId)) {
                is AppResult.Success -> {
                    val merchant = result.data
                    if (merchant == null) {
                        _uiState.value = MerchantProfileUiState.Error("Merchant not found.")
                        return@launch
                    }
                    currentMerchant = merchant
                    // Load merchant's transactions
                    val filters = TransactionFilters(merchantName = merchant.name)
                    transactionRepository.getTransactionsPaged(filters).collect { transactions ->
                        _uiState.value = MerchantProfileUiState.Ready(
                            // Use the latest merchant (may have been toggled) rather than
                            // the snapshot captured when this collector started.
                            merchant = currentMerchant ?: merchant,
                            transactions = transactions,
                        )
                    }
                }
                is AppResult.Failure -> {
                    _uiState.value = MerchantProfileUiState.Error("Failed to load merchant profile.")
                }
                else -> Unit
            }
        }
    }

    fun onToggleFavourite() {
        val current = (_uiState.value as? MerchantProfileUiState.Ready)?.merchant ?: return
        viewModelScope.launch {
            merchantRepository.toggleFavourite(current.upiId)
            // Reload to get updated state
            when (val result = merchantRepository.getMerchantByUpiId(current.upiId)) {
                is AppResult.Success -> {
                    result.data?.let { updated ->
                        currentMerchant = updated
                        val currentState = _uiState.value as? MerchantProfileUiState.Ready ?: return@launch
                        _uiState.value = currentState.copy(merchant = updated)
                    }
                }
                else -> Unit
            }
        }
    }
}
