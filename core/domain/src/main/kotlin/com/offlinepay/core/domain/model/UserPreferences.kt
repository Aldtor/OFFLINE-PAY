package com.offlinepay.core.domain.model

import kotlinx.serialization.Serializable

/**
 * User-facing payment routing preferences passed to the [com.offlinepay.core.domain.payment.RoutingEngine].
 *
 * This is a lightweight projection of [AppSettings] scoped to the routing engine's needs.
 * It contains only the fields required for a single routing decision, decoupling the engine
 * from the full [AppSettings] object.
 *
 * - [manualPaymentMethodOverride]: when non-null, the engine bypasses operator-priority logic
 *   and routes directly to the specified method (Req 4.5, 4.6).
 * - [routingPriorityMap]: per-operator ordered list of [PaymentMethodType] values; overrides
 *   the engine's built-in defaults (Req 4.2, 4.3). An empty map causes the engine to fall
 *   back to the default priority map in Design Section 4.2.
 *
 * Design reference: Section 4.1 (UserPreferences data class), Section 4.2 (routing algorithm)
 * Requirements: Req 4.2 (configurable routing priority list), Req 4.5 (manual override)
 *
 * @param manualPaymentMethodOverride Forces a specific payment method regardless of operator.
 *   Null means automatic routing.
 * @param routingPriorityMap Maps each [OperatorType] to an ordered list of preferred
 *   [PaymentMethodType] values. Absent keys fall back to engine defaults.
 */
@Serializable
data class UserPreferences(
    val manualPaymentMethodOverride: PaymentMethodType? = null,
    val routingPriorityMap: Map<OperatorType, List<PaymentMethodType>> = emptyMap(),
)
