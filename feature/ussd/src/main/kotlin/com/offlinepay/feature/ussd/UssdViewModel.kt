package com.offlinepay.feature.ussd

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinepay.core.domain.model.PaymentParams
import com.offlinepay.core.domain.model.TransactionStatus
import com.offlinepay.core.domain.usecase.merchant.UpsertMerchantUseCase
import com.offlinepay.core.domain.usecase.transaction.UpdateTransactionStatusUseCase
import com.offlinepay.feature.ussd.autodrive.UssdAccessibilitySettings
import com.offlinepay.feature.ussd.autodrive.UssdAutoDriveSession
import com.offlinepay.feature.ussd.autodrive.UssdDriveProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Coordinates the auto-driven `*99#` USSD payment: it launches the session via
 * [UssdController] and mirrors [UssdAutoDriveSession.progress] (published by
 * [com.offlinepay.feature.ussd.autodrive.UssdAccessibilityService]) into [uiState].
 *
 * The PIN step is never automated — when the driver reaches the PIN prompt the UI shows a
 * [UiState.AwaitingPin] callout and the user types their PIN in the system dialog.
 *
 * Design reference: Section 5.5 (UssdViewModel)
 * Requirements: Req 5.3 (session lifecycle), Req 5.7 (timeout / fallback offer),
 *               Req 5.9 (USSD result → update transaction status)
 */
@HiltViewModel
class UssdViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ussdController: UssdController,
    private val autoDriveSession: UssdAutoDriveSession,
    private val updateTransactionStatusUseCase: UpdateTransactionStatusUseCase,
    private val upsertMerchantUseCase: UpsertMerchantUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // -------------------------------------------------------------------------
    // UI state & events
    // -------------------------------------------------------------------------

    sealed class UiState {
        data object Idle : UiState()
        data class Requesting(val countdownSeconds: Int) : UiState()
        data class Active(val statusText: String, val countdownSeconds: Int) : UiState()

        /** All menus auto-filled — the user must now enter their UPI PIN in the system dialog. */
        data object AwaitingPin : UiState()
        data class Completed(val transactionId: String) : UiState()
        data object Failed : UiState()
        data object Timeout : UiState()
        data object Cancelled : UiState()
    }

    sealed class UiEvent {
        data object OfferFallbackTo123Pay : UiEvent()
        data class NavigateToSuccess(val transactionId: String) : UiEvent()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events: Flow<UiEvent> = _events.receiveAsFlow()

    /** Whether the auto-pay accessibility service is enabled — the screen shows an enable gate if false. */
    val isAutoPayEnabled: Boolean = UssdAccessibilitySettings.isServiceEnabled(context)

    private val decodedParams: PaymentParams? = runCatching {
        val json = savedStateHandle.get<String>("paymentParamsJson").orEmpty()
        kotlinx.serialization.json.Json.decodeFromString<PaymentParams>(json)
    }.getOrNull()

    private var transactionId: String? = null
    private var countdown = TIMEOUT_SECONDS
    private var countdownJob: Job? = null
    private var terminalReached = false

    init {
        startSession()
        observeProgress()
    }

    private fun startSession() {
        viewModelScope.launch {
            val params = decodedParams ?: run {
                _uiState.value = UiState.Failed
                return@launch
            }
            val subId = savedStateHandle.get<String>("subscriptionId")?.toIntOrNull() ?: run {
                _uiState.value = UiState.Failed
                return@launch
            }
            transactionId = ussdController.initiateUssd(params, subId)
            _uiState.value = UiState.Requesting(countdown)
            restartCountdown()
        }
    }

    private fun observeProgress() {
        viewModelScope.launch {
            autoDriveSession.progress.collect { progress ->
                when (progress) {
                    is UssdDriveProgress.Idle -> Unit

                    is UssdDriveProgress.Navigating -> {
                        restartCountdown()
                        val suffix = if (progress.totalSteps > 0 && progress.stepIndex > 0) {
                            " (${progress.stepIndex}/${progress.totalSteps})"
                        } else {
                            ""
                        }
                        _uiState.value = UiState.Active(progress.label + suffix, countdown)
                    }

                    is UssdDriveProgress.AwaitingPin -> {
                        countdownJob?.cancel() // give the user unlimited time to enter the PIN
                        _uiState.value = UiState.AwaitingPin
                    }

                    is UssdDriveProgress.Completed -> onCompleted()
                    is UssdDriveProgress.Failed -> onFailed()
                }
            }
        }
    }

    private fun onCompleted() {
        if (terminalReached) return
        terminalReached = true
        countdownJob?.cancel()
        val txId = transactionId
        _uiState.value = UiState.Completed(txId.orEmpty())
        viewModelScope.launch {
            if (txId != null) {
                updateTransactionStatusUseCase(txId, TransactionStatus.SUCCESS)
                decodedParams?.let { upsertMerchantUseCase(it, System.currentTimeMillis()) }
                _events.send(UiEvent.NavigateToSuccess(txId))
            }
        }
    }

    private fun onFailed() {
        if (terminalReached) return
        terminalReached = true
        countdownJob?.cancel()
        _uiState.value = UiState.Failed
        viewModelScope.launch {
            transactionId?.let { updateTransactionStatusUseCase(it, TransactionStatus.FAILURE) }
            _events.send(UiEvent.OfferFallbackTo123Pay)
        }
    }

    /** Restarts the inactivity countdown; on expiry the flow times out and offers 123PAY. */
    private fun restartCountdown() {
        countdown = TIMEOUT_SECONDS
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (countdown > 0) {
                delay(1_000L)
                countdown--
                // Reflect the tick in the current Active/Requesting state.
                when (val s = _uiState.value) {
                    is UiState.Requesting -> _uiState.value = UiState.Requesting(countdown)
                    is UiState.Active -> _uiState.value = UiState.Active(s.statusText, countdown)
                    else -> Unit
                }
            }
            if (!terminalReached) onTimeout()
        }
    }

    private fun onTimeout() {
        if (terminalReached) return
        terminalReached = true
        _uiState.value = UiState.Timeout
        viewModelScope.launch { _events.send(UiEvent.OfferFallbackTo123Pay) }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Opens system Accessibility settings so the user can enable auto-pay. */
    fun openAutoPaySettings() = UssdAccessibilitySettings.openAccessibilitySettings(context)

    /** Cancels the active session. */
    fun cancel() {
        terminalReached = true
        countdownJob?.cancel()
        autoDriveSession.disarm()
        _uiState.value = UiState.Cancelled
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
        autoDriveSession.disarm()
    }

    companion object {
        private const val TIMEOUT_SECONDS = 45
    }
}
