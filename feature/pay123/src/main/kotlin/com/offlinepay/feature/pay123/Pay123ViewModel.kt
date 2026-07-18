package com.offlinepay.feature.pay123

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.domain.error.DomainError
import com.offlinepay.core.domain.model.PaymentParams
import com.offlinepay.core.domain.model.SimInfo
import com.offlinepay.core.domain.model.TransactionStatus
import com.offlinepay.core.domain.usecase.transaction.UpdateTransactionStatusUseCase
import com.offlinepay.core.domain.usecase.merchant.UpsertMerchantUseCase
import androidx.lifecycle.SavedStateHandle
import com.offlinepay.core.domain.usecase.sim.DetectSimUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the 123PAY IVR guidance screen.
 *
 * Manages the [Pay123SessionStateMachine] and [Pay123Controller], translating
 * session-state transitions into [UiState] updates and one-shot [UiEvent]s.
 *
 * ### Lifecycle wiring
 * The screen composable must call [attachLifecycle] with the current [Lifecycle]
 * once — typically inside a `LaunchedEffect(Unit)`.  The state machine then
 * observes `onPause()` / `onResume()` directly to drive the
 * `CALL_IN_PROGRESS → AWAITING_USER_CONFIRMATION` transition.
 *
 * ### UiAction dispatch
 * User actions are dispatched through [onAction]:
 * - [UiAction.InitiateCall] — starts the session (called after nav-arg params are ready).
 * - [UiAction.CallLaunched] — reserved; transition is driven by the lifecycle observer.
 * - [UiAction.ConfirmSuccess] / [UiAction.ConfirmFailure] / [UiAction.DismissWithoutConfirming]
 *   — update the transaction status and navigate to the result screen.
 * - [UiAction.RetryCall] — resets the machine and retries initiation.
 *
 * Design reference: Sections 7.1, 7.2, 10.2 (Pay123ViewModel contracts)
 * Requirements: Req 6.1–6.8
 */
@HiltViewModel
class Pay123ViewModel @Inject constructor(
    private val pay123Controller: Pay123Controller,
    private val updateTransactionStatusUseCase: UpdateTransactionStatusUseCase,
    private val upsertMerchantUseCase: UpsertMerchantUseCase,
    private val detectSimUseCase: DetectSimUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    init {
        autoInitiateCall()
    }

    private fun autoInitiateCall() {
        viewModelScope.launch {
            val json = savedStateHandle.get<String>("paymentParamsJson").orEmpty()
            if (json.isBlank()) return@launch
            val subId = savedStateHandle.get<String>("subscriptionId")?.toIntOrNull() ?: return@launch
            val decodedParams = kotlinx.serialization.json.Json.decodeFromString<com.offlinepay.core.domain.model.PaymentParams>(json)

            // Find SimInfo for subId
            val simsResult = detectSimUseCase()
            if (simsResult is AppResult.Success) {
                val simInfo = simsResult.data.find { it.subscriptionId == subId }
                if (simInfo != null) {
                    onAction(UiAction.InitiateCall(decodedParams, simInfo))
                }
            }
        }
    }

    // ── UiState ─────────────────────────────────────────────────────────────

    /** Snapshot of the current UI to render. */
    sealed class UiState {
        data object Idle : UiState()
        data object InitiatingCall : UiState()
        data class CallInProgress(
            val merchantName: String,
            val amount: Long,
            val upiId: String,
        ) : UiState()
        data class AwaitingConfirmation(
            val merchantName: String,
            val amount: Long,
        ) : UiState()
        data class Completed(val transactionId: String) : UiState()
        data class Failed(val error: DomainError.Pay123Error) : UiState()
    }

    // ── UiEvent ─────────────────────────────────────────────────────────────

    /** One-shot events emitted to the UI layer for navigation / side-effects. */
    sealed class UiEvent {
        data class LaunchPhoneCall(val number: String) : UiEvent()
        data class NavigateToSuccess(val transactionId: String) : UiEvent()
        data class NavigateToFailure(val error: DomainError) : UiEvent()
        data class NavigateToResultConfirmation(val transactionId: String) : UiEvent()
    }

    // ── UiAction ────────────────────────────────────────────────────────────

    /** User-driven actions dispatched from the composable. */
    sealed class UiAction {
        /** Start the 123PAY session with the given payment parameters and SIM. */
        data class InitiateCall(val params: PaymentParams, val simInfo: SimInfo) : UiAction()

        /** Lifecycle signal — the phone call activity was launched. */
        data object CallLaunched : UiAction()

        /** User confirmed the IVR payment succeeded. */
        data object ConfirmSuccess : UiAction()

        /** User confirmed the IVR payment failed. */
        data object ConfirmFailure : UiAction()

        /** User dismissed the confirmation prompt without answering. */
        data object DismissWithoutConfirming : UiAction()

        /** User wants to retry the call after a failure. */
        data object RetryCall : UiAction()
    }

    // ── State & event flows ─────────────────────────────────────────────────

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // ── Session state machine ───────────────────────────────────────────────

    /**
     * The active state machine instance.
     * Created once per lifecycle attachment.
     */
    private var stateMachine: Pay123SessionStateMachine? = null

    /**
     * Cached lifecycle reference used when retrying after a failure.
     * Updated each time [attachLifecycle] is called.
     */
    private var attachedLifecycle: Lifecycle? = null

    /** Saved params needed after lifecycle-driven transitions. */
    private var currentParams: PaymentParams? = null
    private var currentSimInfo: SimInfo? = null

    /**
     * Initiation requested (by [autoInitiateCall]) before [attachLifecycle] ran.
     * SIM detection is asynchronous and can win the race against the composable's
     * `LaunchedEffect`, so we stash the params here and run them once the state
     * machine exists — otherwise [requireStateMachine] would crash on entry.
     */
    private var pendingInitiation: Pair<PaymentParams, SimInfo>? = null

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Wires a fresh [Pay123SessionStateMachine] to the given [lifecycle].
     *
     * Call this from the guidance screen composable's `LaunchedEffect(Unit)`.
     * Subsequent calls are no-ops (guarded by a null-check), so recomposition
     * won't create duplicate state machines.
     */
    fun attachLifecycle(lifecycle: Lifecycle) {
        if (stateMachine != null) return // Already attached — prevent duplicate machines

        attachedLifecycle = lifecycle
        stateMachine = Pay123SessionStateMachine(
            onStateChanged = { state -> onSessionStateChanged(state) },
            lifecycle = lifecycle,
        )

        // Run any initiation that was requested before the lifecycle attached
        // (SIM detection completed first). Safe now that the state machine exists.
        pendingInitiation?.let { (params, simInfo) ->
            pendingInitiation = null
            initiateCall(params, simInfo)
        }
    }

    /**
     * Dispatches a [UiAction] from the composable layer.
     */
    fun onAction(action: UiAction) {
        when (action) {
            is UiAction.InitiateCall             -> initiateCall(action.params, action.simInfo)
            is UiAction.CallLaunched             -> { /* Transition driven by lifecycle observer */ }
            is UiAction.ConfirmSuccess           -> confirmResult(Pay123SessionState.SUCCESS)
            is UiAction.ConfirmFailure           -> confirmResult(Pay123SessionState.FAILED)
            is UiAction.DismissWithoutConfirming -> confirmResult(Pay123SessionState.UNKNOWN)
            is UiAction.RetryCall                -> retryCall()
        }
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private fun initiateCall(params: PaymentParams, simInfo: SimInfo) {
        currentParams = params
        currentSimInfo = simInfo

        val machine = stateMachine
        if (machine == null) {
            // Lifecycle not attached yet — defer until attachLifecycle() creates the
            // state machine, then this initiation is replayed. Prevents a crash when
            // async SIM detection dispatches InitiateCall before the screen attaches.
            pendingInitiation = params to simInfo
            return
        }
        machine.initiate()
        _uiState.value = UiState.InitiatingCall

        viewModelScope.launch {
            val result = pay123Controller.initiate(params, simInfo)
            when (result) {
                is AppResult.Failure -> {
                    // Signal the state machine that the call failed immediately
                    machine.reportCallFailed()
                    // onSessionStateChanged(FAILED) will update UI state
                }
                else -> {
                    // Success: the call was launched.
                    // The lifecycle observer on the state machine will detect onPause()
                    // and transition to CALL_IN_PROGRESS automatically.
                }
            }
        }
    }

    private fun confirmResult(result: Pay123SessionState) {
        requireStateMachine().confirmResult(result)
        // onSessionStateChanged handles the state transition and DB update
    }

    private fun retryCall() {
        // Tear down current machine so that a fresh one can be created
        stateMachine?.reset()
        stateMachine = null
        pay123Controller.reset()
        _uiState.value = UiState.Idle

        val lifecycle = attachedLifecycle ?: return
        val params = currentParams ?: return
        val simInfo = currentSimInfo ?: return

        // Re-attach state machine and re-initiate
        attachedLifecycle = null // Force re-registration
        attachLifecycle(lifecycle)
        initiateCall(params, simInfo)
    }

    /**
     * Callback wired into [Pay123SessionStateMachine.onStateChanged].
     * Translates session states to [UiState] and emits navigation events.
     */
    private fun onSessionStateChanged(state: Pay123SessionState) {
        val params = currentParams
        when (state) {
            Pay123SessionState.IDLE -> _uiState.value = UiState.Idle

            Pay123SessionState.INITIATING_CALL -> _uiState.value = UiState.InitiatingCall

            Pay123SessionState.CALL_IN_PROGRESS -> {
                _uiState.value = UiState.CallInProgress(
                    merchantName = params?.payeeName ?: params?.upiId.orEmpty(),
                    amount       = params?.amount ?: 0L,
                    upiId        = params?.upiId.orEmpty(),
                )
            }

            Pay123SessionState.AWAITING_USER_CONFIRMATION -> {
                _uiState.value = UiState.AwaitingConfirmation(
                    merchantName = params?.payeeName ?: params?.upiId.orEmpty(),
                    amount       = params?.amount ?: 0L,
                )
            }

            Pay123SessionState.SUCCESS ->
                handleTerminalResult(state, TransactionStatus.SUCCESS)

            Pay123SessionState.FAILED ->
                handleTerminalResult(state, TransactionStatus.FAILURE)

            Pay123SessionState.UNKNOWN ->
                handleTerminalResult(state, TransactionStatus.UNKNOWN)
        }
    }

    /**
     * Updates the transaction record and emits the navigation event
     * for terminal session states (SUCCESS / FAILED / UNKNOWN).
     *
     * Requirements: Req 6.6 (SUCCESS persist), Req 6.7 (FAILURE persist), Req 6.8 (UNKNOWN persist)
     */
    private fun handleTerminalResult(
        sessionState: Pay123SessionState,
        status: TransactionStatus,
    ) {
        val transactionId = pay123Controller.pendingTransactionId

        viewModelScope.launch {
            // Persist the final status
            if (transactionId != null) {
                updateTransactionStatusUseCase(transactionId, status)
            }

            when (sessionState) {
                Pay123SessionState.SUCCESS -> {
                    currentParams?.let { params ->
                        upsertMerchantUseCase(params, System.currentTimeMillis())
                    }
                    _uiState.value = UiState.Completed(transactionId.orEmpty())
                    _events.send(UiEvent.NavigateToSuccess(transactionId.orEmpty()))
                }
                Pay123SessionState.FAILED -> {
                    val error = DomainError.Pay123Error.CallFailed("User reported failure")
                    _uiState.value = UiState.Failed(error)
                    _events.send(UiEvent.NavigateToFailure(error))
                }
                Pay123SessionState.UNKNOWN -> {
                    // Navigate to the deferred-resolution screen so the user can
                    // retrospectively mark the transaction as succeeded or failed.
                    // Req 6.8: persist UNKNOWN, navigate to payment_result_confirmation.
                    _uiState.value = UiState.Completed(transactionId.orEmpty())
                    _events.send(UiEvent.NavigateToResultConfirmation(transactionId.orEmpty()))
                }
                else -> Unit
            }

            pay123Controller.reset()
        }
    }

    private fun requireStateMachine(): Pay123SessionStateMachine =
        checkNotNull(stateMachine) {
            "attachLifecycle() must be called before dispatching actions"
        }

    // ── Lifecycle cleanup ───────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        stateMachine?.reset()
        pay123Controller.reset()
        currentParams = null
        currentSimInfo = null
        attachedLifecycle = null
    }
}
