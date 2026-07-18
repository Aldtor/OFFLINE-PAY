package com.offlinepay.feature.payment.engine

import com.offlinepay.core.domain.model.PaymentMethodType
import com.offlinepay.core.domain.model.PaymentParams
import com.offlinepay.core.domain.model.RoutingDecision
import com.offlinepay.core.domain.model.RoutingReason
import com.offlinepay.core.domain.model.SimInfo
import com.offlinepay.core.domain.model.UserPreferences
import com.offlinepay.core.domain.payment.RoutingEngine
import com.offlinepay.core.domain.payment.StrategyRegistry
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default implementation of [RoutingEngine].
 *
 * Applies the routing algorithm from Design Section 4.2:
 * 1. If the user has a manual override set (in [prefs] or in-memory) and the
 *    strategy exists with voice service available → return [RoutingReason.MANUAL_OVERRIDE].
 * 2. If no voice service is available → return [RoutingReason.NO_VOICE_SERVICE].
 * 3. Query [StrategyRegistry] for the operator, filter by capability, select the first
 *    capable strategy and build a fallback chain from the rest → return
 *    [RoutingReason.OPERATOR_PRIORITY].
 * 4. If no capable strategy found → return [RoutingReason.ALL_METHODS_UNAVAILABLE].
 *
 * The in-memory override ([_memoryOverride]) is set/cleared via [setManualOverride] /
 * [clearManualOverride]. Persistence is handled externally by `SettingsRepository`.
 *
 * Design reference: Section 4.1 (RoutingEngine), Section 4.2 (Routing Algorithm)
 * Requirements: Req 4.1–4.11
 */
@Singleton
class RoutingEngineImpl @Inject constructor(
    private val registry: StrategyRegistry,
) : RoutingEngine {

    /**
     * Volatile so that reads on any thread always see the latest value written
     * by [setManualOverride] or [clearManualOverride].
     */
    @Volatile
    private var _memoryOverride: PaymentMethodType? = null

    // ── RoutingEngine ─────────────────────────────────────────────────────────

    override fun route(
        simInfo: SimInfo,
        params: PaymentParams,
        prefs: UserPreferences,
    ): RoutingDecision {
        // Resolve override: in-memory wins over prefs (in-memory is set by this session).
        val override = _memoryOverride ?: prefs.manualPaymentMethodOverride

        // Step 1 — Manual override (only honoured if the overridden strategy is
        // actually capable for this SIM/params, per the documented contract).
        if (override != null) {
            val strategy = registry.get(override)
            if (strategy != null && strategy.canHandle(simInfo, params)) {
                val allStrategies = registry.getStrategiesForOperator(simInfo.operatorType)
                val fallback = allStrategies
                    .filter { it.canHandle(simInfo, params) }
                    .map { it.methodType() }
                    .filter { it != override }
                return RoutingDecision(
                    selectedMethod = override,
                    fallbackChain = fallback,
                    reason = RoutingReason.MANUAL_OVERRIDE,
                    overrideActive = true,
                    operatorType = simInfo.operatorType,
                    simSlotIndex = simInfo.slotIndex,
                )
            }
        }

        // Step 2 — No voice service
        if (!simInfo.voiceServiceAvailable) {
            return RoutingDecision(
                selectedMethod = null,
                fallbackChain = emptyList(),
                reason = RoutingReason.NO_VOICE_SERVICE,
                overrideActive = false,
                operatorType = simInfo.operatorType,
                simSlotIndex = simInfo.slotIndex,
            )
        }

        // Step 3 — Operator-priority routing
        val strategies = registry.getStrategiesForOperator(simInfo.operatorType)
        val capable = strategies.filter { it.canHandle(simInfo, params) }

        if (capable.isEmpty()) {
            return RoutingDecision(
                selectedMethod = null,
                fallbackChain = emptyList(),
                reason = RoutingReason.ALL_METHODS_UNAVAILABLE,
                overrideActive = false,
                operatorType = simInfo.operatorType,
                simSlotIndex = simInfo.slotIndex,
            )
        }

        val selected = capable.first().methodType()
        val fallback = capable.drop(1).map { it.methodType() }
        return RoutingDecision(
            selectedMethod = selected,
            fallbackChain = fallback,
            reason = RoutingReason.OPERATOR_PRIORITY,
            overrideActive = false,
            operatorType = simInfo.operatorType,
            simSlotIndex = simInfo.slotIndex,
        )
    }

    // ── Manual override management ────────────────────────────────────────────

    /**
     * Sets the in-memory manual override. Persistence is the caller's responsibility
     * (typically via `SettingsRepository`).
     */
    override fun setManualOverride(method: PaymentMethodType) {
        _memoryOverride = method
    }

    /**
     * Clears the in-memory manual override. Persistence is the caller's responsibility.
     */
    override fun clearManualOverride() {
        _memoryOverride = null
    }
}
