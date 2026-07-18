package com.offlinepay.feature.payment

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.designsystem.components.SimDisplayInfo
import com.offlinepay.core.domain.model.AppSettings
import com.offlinepay.core.domain.model.PaymentMethodType
import com.offlinepay.core.domain.model.PaymentParams
import com.offlinepay.core.domain.model.QrType
import com.offlinepay.core.domain.model.RoutingDecision
import com.offlinepay.core.domain.model.SimInfo
import com.offlinepay.core.domain.usecase.payment.GetRoutingDecisionUseCase
import com.offlinepay.core.domain.usecase.settings.GetSettingsUseCase
import com.offlinepay.core.domain.usecase.sim.DetectSimUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.net.URLDecoder
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
// UI contracts
// ─────────────────────────────────────────────────────────────────────────────

/**
 * One-shot events emitted by [PaymentConfirmationViewModel].
 *
 * Collected via a [Channel] so events are delivered exactly once even across
 * recompositions.
 *
 * Design reference: Section 10.2 (PaymentConfirmation UiEvent)
 */
sealed class PaymentConfirmationEvent {
    /** Navigate to the USSD payment flow with the given [params]. */
    data class NavigateToUssd(val params: PaymentParams, val subscriptionId: Int) : PaymentConfirmationEvent()

    /** Navigate to the 123PAY payment flow with the given [params]. */
    data class NavigateToPay123(val params: PaymentParams, val subscriptionId: Int) : PaymentConfirmationEvent()

    /** Show a generic error snackbar / dialog. */
    data object ShowError : PaymentConfirmationEvent()
}

/**
 * UI state for the Payment Confirmation screen.
 *
 * Design reference: Section 10.2 (PaymentConfirmation UiState)
 */
sealed class PaymentConfirmationUiState {

    /** Initial loading while SIM detection and routing are in progress. */
    data object Loading : PaymentConfirmationUiState()

    /**
     * All data is ready and the screen is interactive.
     *
     * @param paymentParams   Parsed parameters from the scanned QR code.
     * @param sims            Display-layer SIM list (mapped from [SimInfo]).
     * @param selectedSim     The currently selected SIM for this payment.
     * @param routingDecision Routing decision for [selectedSim].
     * @param editableAmountStr Current string value of the amount field.
     * @param amountError     Non-null when amount validation fails; shown below the input.
     * @param isStaticQr      True when the QR has no pre-set amount — user must enter one.
     */
    @Immutable
    data class Ready(
        val paymentParams: PaymentParams,
        val sims: List<SimDisplayInfo>,
        val selectedSim: SimDisplayInfo?,
        val routingDecision: RoutingDecision?,
        val editableAmountStr: String,
        val amountError: String?,
        val isStaticQr: Boolean,
    ) : PaymentConfirmationUiState()

    /** An unrecoverable error occurred (e.g. no SIM detected). */
    data class Error(val message: String) : PaymentConfirmationUiState()
}

/**
 * User actions dispatched from [PaymentConfirmationScreen] to the ViewModel.
 *
 * Design reference: Section 10.2 (PaymentConfirmation UiAction)
 */
sealed class PaymentConfirmationAction {
    /** Amount text field changed to [value]. */
    data class AmountChanged(val value: String) : PaymentConfirmationAction()

    /** User tapped a different SIM in [SimSelector]. */
    data class SimSelected(val sim: SimDisplayInfo) : PaymentConfirmationAction()

    /** User tapped the Confirm Payment button. */
    data object ConfirmPayment : PaymentConfirmationAction()
}

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

/**
 * ViewModel for the Payment Confirmation screen.
 *
 * Responsibilities:
 * - Detect available SIMs and pick a default on init.
 * - Compute an initial [RoutingDecision] for the first SIM.
 * - Expose the mutable UI state as a [StateFlow].
 * - Emit one-shot [PaymentConfirmationEvent]s through a [Channel].
 * - Handle all user [PaymentConfirmationAction]s.
 * - Validate the payment amount per NPCI rules (Req 7.3).
 *
 * Design reference: Section 10.2 (PaymentConfirmationViewModel)
 * Requirements: Req 7.1–7.7, Req 9.23
 */
@HiltViewModel
class PaymentConfirmationViewModel @Inject constructor(
    private val detectSimUseCase: DetectSimUseCase,
    private val getRoutingDecisionUseCase: GetRoutingDecisionUseCase,
    private val getSettingsUseCase: GetSettingsUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow<PaymentConfirmationUiState>(
        PaymentConfirmationUiState.Loading,
    )
    val uiState: StateFlow<PaymentConfirmationUiState> = _uiState.asStateFlow()

    /** Buffered channel for one-shot events — capacity 1 so events are not dropped on config change. */
    private val _events = Channel<PaymentConfirmationEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /**
     * In-memory cache of the last-fetched [AppSettings], updated lazily.
     * Used to pass to [GetRoutingDecisionUseCase] on re-routing.
     */
    private var cachedSettings: AppSettings = AppSettings()

    /**
     * Full [SimInfo] list kept in parallel with [SimDisplayInfo] so that we can pass
     * [SimInfo] (with operator type) to the routing engine without storing it in UI state.
     */
    private var simInfoList: List<SimInfo> = emptyList()

    /**
     * [PaymentParams] decoded by the QR scanner and forwarded to this screen via
     * `SavedStateHandle`.
     *
     * Two sources are supported, in order:
     * 1. A direct `PaymentParams` object under key "paymentParams" (used by unit tests).
     * 2. The JSON string nav argument "paymentParamsJson" set by the navigation graph.
     *
     * The JSON path is the one that works in production: relying solely on a
     * `putSerializable("paymentParams", …)` mutation of the nav arguments Bundle at
     * composition time is unreliable — the `SavedStateHandle` is created from the
     * arguments before that mutation is visible, so the object never arrives and the
     * screen fell back to the empty placeholder (blank payee, static-amount field).
     *
     * Falls back to a minimal placeholder when neither is present so the ViewModel does
     * not crash during unit testing with an empty [SavedStateHandle].
     */
    private val paymentParams: PaymentParams
        get() = savedStateHandle.get<PaymentParams>("paymentParams")
            ?: savedStateHandle.get<String>("paymentParamsJson")?.let(::decodePaymentParams)
            ?: PaymentParams(
                upiId = "",
                qrType = QrType.STATIC,
            )

    /**
     * Decodes [PaymentParams] from the nav-argument string. Navigation's string arg may
     * arrive either already URL-decoded or still URL-encoded depending on the graph, so we
     * try the raw value first and fall back to URL-decoding it. Returns null if neither
     * yields valid JSON.
     */
    private fun decodePaymentParams(raw: String): PaymentParams? =
        runCatching { Json.decodeFromString(PaymentParams.serializer(), raw) }.getOrNull()
            ?: runCatching {
                Json.decodeFromString(
                    PaymentParams.serializer(),
                    URLDecoder.decode(raw, "UTF-8"),
                )
            }.getOrNull()

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            // Fetch settings once (the flow emits the current value immediately).
            val settingsResult = runCatching { getSettingsUseCase().first() }
            cachedSettings = settingsResult.getOrDefault(AppSettings())

            // Detect SIMs.
            when (val simResult = detectSimUseCase()) {
                is AppResult.Success -> {
                    simInfoList = simResult.data
                    val displaySims = simResult.data.map { it.toDisplayInfo() }
                    val firstSim = simResult.data.firstOrNull()
                    val firstDisplaySim = displaySims.firstOrNull()

                    // Route for the first SIM.
                    val routing = firstSim?.let { routeForSim(it) }

                    val params = paymentParams
                    val isStaticQr = params.amount == null

                    _uiState.value = PaymentConfirmationUiState.Ready(
                        paymentParams = params,
                        sims = displaySims,
                        selectedSim = firstDisplaySim,
                        routingDecision = routing,
                        editableAmountStr = if (isStaticQr) "" else params.amountAsString(),
                        amountError = null,
                        isStaticQr = isStaticQr,
                    )
                }

                is AppResult.Failure -> {
                    _uiState.value = PaymentConfirmationUiState.Error(
                        message = "No SIM detected. Please insert a SIM card and try again.",
                    )
                }

                is AppResult.Loading -> Unit // shouldn't happen for DetectSimUseCase
            }
        }
    }

    // ── Action handling ───────────────────────────────────────────────────────

    fun onAction(action: PaymentConfirmationAction) {
        when (action) {
            is PaymentConfirmationAction.AmountChanged -> handleAmountChanged(action.value)
            is PaymentConfirmationAction.SimSelected -> handleSimSelected(action.sim)
            is PaymentConfirmationAction.ConfirmPayment -> handleConfirmPayment()
        }
    }

    private fun handleAmountChanged(raw: String) {
        val current = _uiState.value as? PaymentConfirmationUiState.Ready ?: return
        val error = validateAmountString(raw)
        _uiState.value = current.copy(editableAmountStr = raw, amountError = error)
    }

    private fun handleSimSelected(sim: SimDisplayInfo) {
        val current = _uiState.value as? PaymentConfirmationUiState.Ready ?: return
        // Find the full SimInfo that matches the selection.
        val simInfo = simInfoList.firstOrNull { it.subscriptionId == sim.subscriptionId }
            ?: return

        _uiState.value = current.copy(selectedSim = sim, routingDecision = null)

        viewModelScope.launch {
            val routing = routeForSim(simInfo)
            val latest = _uiState.value as? PaymentConfirmationUiState.Ready ?: return@launch
            _uiState.value = latest.copy(routingDecision = routing)
        }
    }

    private fun handleConfirmPayment() {
        val current = _uiState.value as? PaymentConfirmationUiState.Ready ?: return

        val amountStr = current.editableAmountStr
        val error = validateAmountString(amountStr)
        if (error != null) {
            _uiState.value = current.copy(amountError = error)
            return
        }

        // Build the final PaymentParams with the user-supplied amount (if static QR).
        val finalParams = if (current.isStaticQr) {
            // setScale(2) before converting so a value like 100.50 is exact; validation
            // above already rejects >2 decimals, so HALF_UP never actually rounds here.
            val paise = amountStr.toBigDecimal()
                .setScale(2, java.math.RoundingMode.HALF_UP)
                .multiply(java.math.BigDecimal("100"))
                .toLong()
            current.paymentParams.copy(amount = paise)
        } else {
            current.paymentParams
        }

        val subId = current.selectedSim?.subscriptionId ?: 0
        val selectedMethod = current.routingDecision?.selectedMethod
        viewModelScope.launch {
            when (selectedMethod) {
                PaymentMethodType.USSD ->
                    _events.send(PaymentConfirmationEvent.NavigateToUssd(finalParams, subId))

                PaymentMethodType.PAY123 ->
                    _events.send(PaymentConfirmationEvent.NavigateToPay123(finalParams, subId))

                null ->
                    _events.send(PaymentConfirmationEvent.ShowError)
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Calls [GetRoutingDecisionUseCase] and returns the [RoutingDecision] or null on error.
     */
    private suspend fun routeForSim(simInfo: SimInfo): RoutingDecision? {
        return when (
            val result = getRoutingDecisionUseCase(
                simInfo = simInfo,
                params = paymentParams,
                settings = cachedSettings,
            )
        ) {
            is AppResult.Success -> result.data
            else -> null
        }
    }

    /**
     * Maps a domain [SimInfo] to a design-system [SimDisplayInfo].
     */
    private fun SimInfo.toDisplayInfo() = SimDisplayInfo(
        slotIndex = slotIndex,
        operatorName = operatorName,
        isVoiceServiceAvailable = voiceServiceAvailable,
        subscriptionId = subscriptionId,
    )

    /**
     * Converts the [amount] (paise) stored in [PaymentParams] back to a rupee string for display.
     * Uses [java.math.BigDecimal] to avoid floating-point rounding errors in financial amounts.
     */
    private fun PaymentParams.amountAsString(): String {
        val paise = amount ?: 0L
        val rupees = java.math.BigDecimal(paise).divide(
            java.math.BigDecimal("100"),
            2,
            java.math.RoundingMode.UNNECESSARY,
        )
        return if (rupees.scale() == 0 || rupees.stripTrailingZeros().scale() <= 0) {
            rupees.toLong().toString()
        } else {
            rupees.toPlainString()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Amount validation — Req 7.3
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Validates [raw] as a payment amount string.
     *
     * Rules (Req 7.3, NPCI limits):
     * - Blank → "Please enter an amount"
     * - Non-numeric → "Enter a valid number"
     * - Zero → "Amount must be greater than ₹0"
     * - Negative → "Amount must be greater than ₹0"
     * - > 100000 → "Amount exceeds the NPCI limit of ₹1,00,000"
     *
     * @return A human-readable error string, or null if the amount is valid.
     */
    fun validateAmountString(raw: String): String? {
        if (raw.isBlank()) return "Please enter an amount"

        val value = raw.toBigDecimalOrNull()
            ?: return "Enter a valid number"

        return when {
            value <= java.math.BigDecimal.ZERO -> "Amount must be greater than ₹0"
            value.scale() > 2 -> "Amount can have at most 2 decimal places"
            value > java.math.BigDecimal("100000") -> "Amount exceeds the NPCI limit of ₹1,00,000"
            else -> null
        }
    }
}
