package com.offlinepay.feature.dashboard

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinepay.core.common.dispatchers.AppDispatchers
import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.connectivity.ConnectivityMonitor
import com.offlinepay.core.connectivity.ConnectivityState
import com.offlinepay.core.domain.model.MerchantProfile
import com.offlinepay.core.domain.model.OperatorType
import com.offlinepay.core.domain.model.SimInfo
import com.offlinepay.core.domain.model.TransactionStatus
import com.offlinepay.core.domain.usecase.integrity.GetIntegrityVerdictUseCase
import com.offlinepay.core.domain.usecase.integrity.IntegrityVerdictResult
import com.offlinepay.core.domain.usecase.merchant.GetFavouriteMerchantsUseCase
import com.offlinepay.core.domain.usecase.merchant.GetRecentMerchantsUseCase
import com.offlinepay.core.domain.usecase.sim.DetectSimUseCase
import com.offlinepay.core.domain.usecase.transaction.GetThisMonthTransactionsUseCase
import com.offlinepay.core.domain.usecase.transaction.GetTodayTransactionsUseCase
import com.offlinepay.core.telephony.OperatorResolver
import com.offlinepay.core.telephony.VoiceServiceChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// ── Supporting data types ──────────────────────────────────────────────────────

/**
 * Summary of the most recent payment for the dashboard "last payment" card.
 *
 * Requirements: Req 17.3 (last payment summary)
 */
data class LastPaymentSummary(
    val merchantName: String,
    val amountPaise: Long,
    val status: TransactionStatus,
    val timestampMs: Long,
)

/**
 * Represents the offline payment capability based on SIM operator and voice service.
 *
 * Design reference: Section 10.2 (DashboardViewModel)
 * Requirements: Req 17.8, Req 17.9
 */
sealed class SimPaymentStatus {
    /** USSD (*99#) is available for this operator with active voice service. */
    data class UssdAvailable(val operatorName: String) : SimPaymentStatus()

    /** 123PAY IVR is available with active voice service. */
    data class Pay123Available(val operatorName: String) : SimPaymentStatus()

    /** No cellular voice service available — neither payment method can be used. */
    data object NoService : SimPaymentStatus()
}

// ── UI State / Event / Action contracts ────────────────────────────────────────

/**
 * UI state for the dashboard screen.
 *
 * Design reference: Section 10.2 (DashboardViewModel contract)
 * Requirements: Req 17.1–17.10
 */
sealed class DashboardUiState {

    /** Initial state while data is loading. Skeleton cards are shown. */
    data object Loading : DashboardUiState()

    /**
     * All data loaded and available for display.
     *
     * @param todayCount Number of transactions today.
     * @param todayTotalPaise Sum of all today's transaction amounts in paise.
     * @param monthCount Number of transactions this calendar month.
     * @param monthTotalPaise Sum of all this month's transaction amounts in paise.
     * @param lastPayment The most recent transaction summary, or null if no transactions.
     * @param recentMerchants Last 5 unique merchants (Req 17.4).
     * @param favouriteMerchants User's starred merchants (Req 17.5).
     * @param simStatus Current offline payment capability based on SIM/voice detection.
     * @param connectivityState Current network connectivity state from ConnectivityMonitor.
     * @param securityTier Security tier derived from Play Integrity verdict staleness
     *   (1 = fresh PASS, 2 = stale 24-72h, 3 = very stale >72h or no verdict, 4 = FAIL).
     * @param connectivityBannerDismissed True after user dismisses the connectivity banner.
     * @param securityBannerDismissed True after user dismisses the Tier-2 security banner.
     *   Tier-3 banners are non-dismissible per Req 9.4.
     */
    @Immutable
    data class Ready(
        val todayCount: Int,
        val todayTotalPaise: Long,
        val monthCount: Int,
        val monthTotalPaise: Long,
        val lastPayment: LastPaymentSummary?,
        val recentMerchants: List<MerchantProfile>,
        val favouriteMerchants: List<MerchantProfile>,
        val simStatus: SimPaymentStatus,
        val connectivityState: ConnectivityState,
        val securityTier: Int,
        val connectivityBannerDismissed: Boolean,
        val securityBannerDismissed: Boolean,
    ) : DashboardUiState()

    /** A non-recoverable error occurred loading dashboard data. */
    data class Error(val message: String) : DashboardUiState()
}

/**
 * One-shot navigation and side-effect events emitted by [DashboardViewModel].
 *
 * Design reference: Section 10.2 (DashboardViewModel contract)
 * Requirements: Req 17.6, Req 17.7
 */
sealed class DashboardUiEvent {
    /** Navigate to the QR scanner screen. */
    data object NavigateToScanner : DashboardUiEvent()

    /** Navigate to the transaction history screen. */
    data object NavigateToHistory : DashboardUiEvent()

    /**
     * Navigate to the merchant profile screen for [upiId].
     *
     * @param upiId The UPI ID of the merchant to view.
     */
    data class NavigateToMerchantProfile(val upiId: String) : DashboardUiEvent()

    /** Navigate to the settings screen. */
    data object NavigateToSettings : DashboardUiEvent()
}

/**
 * User interactions dispatched to [DashboardViewModel.onAction].
 *
 * Design reference: Section 10.2 (DashboardViewModel contract)
 */
sealed class DashboardUiAction {
    /** User tapped the "Scan QR" button. */
    data object ScanQr : DashboardUiAction()

    /** User tapped the "View History" button or chip. */
    data object ViewHistory : DashboardUiAction()

    /**
     * User tapped a merchant card.
     *
     * @param upiId The UPI ID of the tapped merchant.
     */
    data class OpenMerchantProfile(val upiId: String) : DashboardUiAction()

    /** User tapped the settings icon. */
    data object OpenSettings : DashboardUiAction()

    /** User dismissed the connectivity information banner (Req 1.2). */
    data object DismissConnectivityBanner : DashboardUiAction()

    /** User dismissed the security notice banner (valid only for Tier-2 per Req 9.3). */
    data object DismissSecurityBanner : DashboardUiAction()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

/**
 * ViewModel for the dashboard (home) screen.
 *
 * Combines several domain flows into a single [StateFlow<DashboardUiState>]:
 * - [GetRecentMerchantsUseCase] for the recent merchants section
 * - [GetFavouriteMerchantsUseCase] for the favourites section
 * - [GetTodayTransactionsUseCase] for today's payment count and total
 * - [GetThisMonthTransactionsUseCase] for this month's payment count and total
 * - [ConnectivityMonitor.connectivityState] for the connectivity banner
 * - [GetIntegrityVerdictUseCase] for the security tier banner
 * - [DetectSimUseCase] for the offline payment status indicator
 *
 * All data loading is dispatched to [AppDispatchers.io] per the design contract.
 *
 * Design reference: Section 10.2 (DashboardViewModel), Section 11.4
 * Requirements: Req 1.2, Req 9.3, Req 9.4, Req 17.1–17.10
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val getRecentMerchantsUseCase: GetRecentMerchantsUseCase,
    private val getFavouriteMerchantsUseCase: GetFavouriteMerchantsUseCase,
    private val getTodayTransactionsUseCase: GetTodayTransactionsUseCase,
    private val getThisMonthTransactionsUseCase: GetThisMonthTransactionsUseCase,
    private val connectivityMonitor: ConnectivityMonitor,
    private val getIntegrityVerdictUseCase: GetIntegrityVerdictUseCase,
    private val detectSimUseCase: DetectSimUseCase,
    private val voiceServiceChecker: VoiceServiceChecker,
    private val operatorResolver: OperatorResolver,
    private val dispatchers: AppDispatchers,
) : ViewModel() {

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)

    /** Publicly exposed UI state, always replays last emission to new collectors. */
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    /**
     * One-shot navigation/side-effect events. Collected as a hot [kotlinx.coroutines.flow.Flow]
     * via [receiveAsFlow]. Buffered so events are not dropped if the UI is briefly suspended.
     */
    private val _events = Channel<DashboardUiEvent>(BUFFERED)
    val events = _events.receiveAsFlow()

    // ── Internal mutable state for banner dismissal (ephemeral, not persisted) ──

    private val connectivityBannerDismissed = MutableStateFlow(false)
    private val securityBannerDismissed = MutableStateFlow(false)

    init {
        observeDashboardData()
    }

    // ── Data loading ───────────────────────────────────────────────────────────

    /**
     * Combines all domain flows into a single emission and updates [_uiState].
     *
     * Uses [combine] so any upstream emission (new transaction, connectivity change, etc.)
     * causes the full state to be recomputed and emitted.
     *
     * Heavy one-shot calls ([getIntegrityVerdictUseCase], [detectSimUseCase]) are wrapped in
     * cold flows so they participate in the combine pipeline.
     */
    private fun observeDashboardData() {
        viewModelScope.launch(dispatchers.io) {
            try {
                // One-shot data resolved once on startup (wrapped as cold flows for combine)
                val integrityResult = getIntegrityVerdictUseCase()
                val securityTier = computeSecurityTier(integrityResult)

                val simStatusResult = detectSimUseCase()
                val simStatus = computeSimPaymentStatus(simStatusResult)

                // Combine all reactive flows.
                // Kotlin's combine has typed overloads only up to 5 flows; for 7 we
                // nest two combine calls to keep full type safety.
                val txnFlow = combine(
                    getTodayTransactionsUseCase(),
                    getThisMonthTransactionsUseCase(),
                ) { todayTxns, monthTxns -> Pair(todayTxns, monthTxns) }

                val merchantFlow = combine(
                    getRecentMerchantsUseCase(),
                    getFavouriteMerchantsUseCase(),
                ) { recent, favourites -> Pair(recent, favourites) }

                combine(
                    txnFlow,
                    merchantFlow,
                    connectivityMonitor.connectivityState,
                    connectivityBannerDismissed,
                    securityBannerDismissed,
                ) { txnPair, merchantPair, connectivityState, connDismissed, secDismissed ->
                    val todayTxns = txnPair.first
                    val monthTxns = txnPair.second
                    val recentMerchants = merchantPair.first
                    val favourites = merchantPair.second

                    val todayCount = todayTxns.size
                    val todayTotalPaise = todayTxns.sumOf { it.amountPaise }
                    val monthCount = monthTxns.size
                    val monthTotalPaise = monthTxns.sumOf { it.amountPaise }

                    // Last payment: most recent by timestamp across all-time data available
                    // We use today+month combined; fallback: the most recent in either list
                    val allRecentTxns = (todayTxns + monthTxns)
                        .distinctBy { it.id }
                        .sortedByDescending { it.timestampMs }
                    val lastPayment = allRecentTxns.firstOrNull()?.let { txn ->
                        LastPaymentSummary(
                            merchantName = txn.merchantName ?: txn.payeeName ?: txn.payeeUpiId,
                            amountPaise = txn.amountPaise,
                            status = txn.status,
                            timestampMs = txn.timestampMs,
                        )
                    }

                    DashboardUiState.Ready(
                        todayCount = todayCount,
                        todayTotalPaise = todayTotalPaise,
                        monthCount = monthCount,
                        monthTotalPaise = monthTotalPaise,
                        lastPayment = lastPayment,
                        recentMerchants = recentMerchants,
                        favouriteMerchants = favourites,
                        simStatus = simStatus,
                        connectivityState = connectivityState,
                        securityTier = securityTier,
                        connectivityBannerDismissed = connDismissed,
                        securityBannerDismissed = secDismissed,
                    )
                }.collect { readyState ->
                    _uiState.value = readyState
                }
            } catch (e: Exception) {
                _uiState.value = DashboardUiState.Error(
                    message = e.message ?: "Failed to load dashboard data",
                )
            }
        }
    }

    // ── Action handler ─────────────────────────────────────────────────────────

    /**
     * Entry point for all user interactions from the dashboard screen.
     *
     * @param action The [DashboardUiAction] dispatched by the UI.
     */
    fun onAction(action: DashboardUiAction) {
        when (action) {
            DashboardUiAction.ScanQr -> sendEvent(DashboardUiEvent.NavigateToScanner)
            DashboardUiAction.ViewHistory -> sendEvent(DashboardUiEvent.NavigateToHistory)
            is DashboardUiAction.OpenMerchantProfile ->
                sendEvent(DashboardUiEvent.NavigateToMerchantProfile(action.upiId))
            DashboardUiAction.OpenSettings -> sendEvent(DashboardUiEvent.NavigateToSettings)
            DashboardUiAction.DismissConnectivityBanner -> connectivityBannerDismissed.value = true
            DashboardUiAction.DismissSecurityBanner -> {
                // Only Tier-2 (stale 24–72h) is dismissible per Req 9.3.
                // Tier-3 (>72h) banner is persistent — dismissal is silently ignored.
                val currentState = _uiState.value
                if (currentState is DashboardUiState.Ready && currentState.securityTier == 2) {
                    securityBannerDismissed.value = true
                }
            }
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private fun sendEvent(event: DashboardUiEvent) {
        viewModelScope.launch {
            _events.send(event)
        }
    }

    /**
     * Derives the integer security tier (1–4) from the [IntegrityVerdictResult]:
     *
     * - **Tier 1**: Fresh PASS verdict (ageMs < 24h) → no warning
     * - **Tier 2**: Stale PASS verdict (24h ≤ ageMs < 72h) → dismissible amber notice
     * - **Tier 3**: Very stale (ageMs ≥ 72h) or no cached verdict → persistent red banner
     * - **Tier 4**: FAIL verdict → hard block (handled upstream by SecurityGuard)
     *
     * Design reference: Section 8.2 (5-tier Play Integrity strategy)
     * Requirements: Req 9.2, Req 9.3, Req 9.4, Req 9.5
     */
    private fun computeSecurityTier(
        result: AppResult<IntegrityVerdictResult, *>,
    ): Int {
        val verdictResult = (result as? AppResult.Success)?.data
            ?: return 3 // Storage error — treat as no verdict

        val verdict = verdictResult.verdict
        val ageMs = verdictResult.ageMs

        if (verdict == null) return 3 // No cached verdict

        return when {
            verdict.verdict == com.offlinepay.core.domain.model.VerdictType.FAIL -> 4
            ageMs < TWENTY_FOUR_HOURS_MS -> 1
            ageMs < SEVENTY_TWO_HOURS_MS -> 2
            else -> 3
        }
    }

    /**
     * Derives the [SimPaymentStatus] from the [DetectSimUseCase] result.
     *
     * - Jio SIMs default to 123PAY per the routing priority map (Req 4.3)
     * - All other operators with voice service → USSD available
     * - No SIM or no service → NoService
     *
     * Design reference: Section 4.2 (Default Routing Priority Map)
     * Requirements: Req 17.8, Req 17.9
     */
    private suspend fun computeSimPaymentStatus(
        result: AppResult<List<SimInfo>, *>,
    ): SimPaymentStatus = withContext(dispatchers.default) {
        val simList = (result as? AppResult.Success)?.data
        if (simList.isNullOrEmpty()) return@withContext SimPaymentStatus.NoService

        // Prefer the first SIM with voice service
        val activeSim = simList.firstOrNull { it.voiceServiceAvailable }
            ?: return@withContext SimPaymentStatus.NoService

        return@withContext when (activeSim.operatorType) {
            OperatorType.JIO -> SimPaymentStatus.Pay123Available(activeSim.operatorName)
            else -> SimPaymentStatus.UssdAvailable(activeSim.operatorName)
        }
    }

    // ── Constants ──────────────────────────────────────────────────────────────

    private companion object {
        /** 24 hours in milliseconds. */
        const val TWENTY_FOUR_HOURS_MS = 24L * 60 * 60 * 1_000

        /** 72 hours in milliseconds. */
        const val SEVENTY_TWO_HOURS_MS = 72L * 60 * 60 * 1_000
    }
}
