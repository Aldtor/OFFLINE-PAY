package com.offlinepay.core.domain.payment

import com.offlinepay.core.domain.model.PaymentMethodType
import com.offlinepay.core.domain.model.PaymentParams
import com.offlinepay.core.domain.model.RoutingDecision
import com.offlinepay.core.domain.model.SimInfo
import com.offlinepay.core.domain.model.UserPreferences

/**
 * The intelligent Offline Payment Engine that selects the appropriate payment method.
 *
 * Implements the routing algorithm from Design Section 4.2:
 * 1. Check manual override → use it if set and capable
 * 2. Check voice service availability → error if no service
 * 3. Look up operator in configurable priority list
 * 4. Select first capable strategy
 * 5. Build fallback chain from remaining capable strategies
 * 6. Return [RoutingDecision] with selected method and fallback chain
 *
 * Manual overrides can be set at runtime via [setManualOverride] and cleared
 * via [clearManualOverride]; they are also persisted in [UserPreferences].
 *
 * This is an interface so the implementation can be tested with fake strategies
 * without Android dependencies.
 *
 * Design reference: Section 4.1 (RoutingEngine interface), Section 4.2 (Routing Algorithm)
 * Requirements: Req 4.1–4.11
 */
interface RoutingEngine {

    /**
     * Computes a [RoutingDecision] for the given [simInfo] and [params].
     *
     * @param simInfo The active SIM information (operator, voice service availability).
     * @param params The payment parameters extracted from the QR code.
     * @param prefs Current user preferences, containing the routing priority map
     *   and any active manual override.
     * @return A [RoutingDecision] containing the selected method and fallback chain.
     *   [RoutingDecision.selectedMethod] is null if no method is available.
     */
    fun route(
        simInfo: SimInfo,
        params: PaymentParams,
        prefs: UserPreferences,
    ): RoutingDecision

    /**
     * Sets a manual override that forces the engine to always select [method],
     * bypassing the normal operator-based priority routing.
     *
     * The override is applied for the next call to [route]. Implementations should
     * also persist the override to `EncryptedSharedPreferences` (Req 4.5, 4.6).
     *
     * @param method The [PaymentMethodType] to force for all subsequent payments.
     */
    fun setManualOverride(method: PaymentMethodType)

    /**
     * Clears any active manual override, restoring automatic operator-priority routing.
     *
     * Implementations should also clear the persisted override from
     * `EncryptedSharedPreferences` (Req 4.5, 4.6).
     */
    fun clearManualOverride()
}
