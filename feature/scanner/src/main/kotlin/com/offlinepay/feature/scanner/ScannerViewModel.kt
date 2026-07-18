package com.offlinepay.feature.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinepay.core.domain.model.PaymentParams
import com.offlinepay.core.domain.model.QrParseResult
import com.offlinepay.core.domain.usecase.qr.ParseQrCodeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val parseQrCodeUseCase: ParseQrCodeUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScannerUiState>(ScannerUiState.Loading)
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ScannerUiEvent>()
    val events: SharedFlow<ScannerUiEvent> = _events.asSharedFlow()

    fun onAction(action: ScannerUiAction) = when (action) {
        is ScannerUiAction.FrameAnalysed -> processFrame(action.rawContent)
        ScannerUiAction.ToggleTorch -> toggleTorch()
        ScannerUiAction.PermissionGranted -> _uiState.value = ScannerUiState.Ready(isCameraPermissionGranted = true)
        ScannerUiAction.PermissionDenied -> _uiState.value = ScannerUiState.PermissionRequired
        ScannerUiAction.RescanRequested -> resetToReady()
    }

    private fun processFrame(raw: String) {
        viewModelScope.launch {
            when (val result = parseQrCodeUseCase(raw)) {
                is QrParseResult.Success -> _events.emit(ScannerUiEvent.NavigateToPaymentConfirmation(result.paymentParams))
                is QrParseResult.Failure -> _uiState.value = ScannerUiState.ParseError(
                    message = result.error::class.simpleName ?: "Parse error",
                    detectedType = result.detectedContentType
                )
            }
        }
    }

    private fun toggleTorch() {
        val current = _uiState.value
        if (current is ScannerUiState.Ready) _uiState.value = current.copy(isTorchOn = !current.isTorchOn)
    }

    private fun resetToReady() {
        if (_uiState.value is ScannerUiState.ParseError) _uiState.value = ScannerUiState.Ready(isCameraPermissionGranted = true)
    }
}

sealed class ScannerUiState {
    data object Loading : ScannerUiState()
    data class Ready(val isTorchOn: Boolean = false, val isCameraPermissionGranted: Boolean = false) : ScannerUiState()
    data class ParseError(val message: String, val detectedType: String? = null) : ScannerUiState()
    data object PermissionRequired : ScannerUiState()
}

sealed class ScannerUiEvent {
    data class NavigateToPaymentConfirmation(val paymentParams: PaymentParams) : ScannerUiEvent()
}

sealed class ScannerUiAction {
    data class FrameAnalysed(val rawContent: String) : ScannerUiAction()
    data object ToggleTorch : ScannerUiAction()
    data object PermissionGranted : ScannerUiAction()
    data object PermissionDenied : ScannerUiAction()
    data object RescanRequested : ScannerUiAction()
}
