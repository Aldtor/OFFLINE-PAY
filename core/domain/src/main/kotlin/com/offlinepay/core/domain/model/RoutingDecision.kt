package com.offlinepay.core.domain.model

import kotlinx.serialization.Serializable

/**
 * The result of the [RoutingEngine] determining which offline payment method to use.
 *
 * Contains the selected method, an ordered fallback chain, and metadata explaining
 * why this routing decision was made.
 *
 * Design reference: Section 4.4 (RoutingDecision data model), Section 4.2 (routing algorithm)
 * Requirements: Req 4.1–4.11
 *
 * @param selectedMethod The primary method to attempt, or null if no method is available.
 * @param fallbackChain Ordered list of fallback methods to try if [selectedMethod] fails.
 * @param reason Why this routing decision was made.
 * @param overrideActive True if a manual user override was applied instead of operator defaults.
 * @param operatorType The operator type used to make this decision.
 * @param simSlotIndex The SIM slot index this decision is for.
 */
@Serializable
data class RoutingDecision(
    val selectedMethod: PaymentMethodType?,
    val fallbackChain: List<PaymentMethodType> = emptyList(),
    val reason: RoutingReason,
    val overrideActive: Boolean = false,
    val operatorType: OperatorType,
    val simSlotIndex: Int,
)

/**
 * Reason explaining why a particular [RoutingDecision] was made.
 *
 * Design reference: Section 4.2 (routing algorithm steps)
 */
@Serializable
enum class RoutingReason {
    /** User has set a manual override for the payment method (Req 4.5). */
    MANUAL_OVERRIDE,

    /** Default routing based on operator priority list (Req 4.2, 4.3). */
    OPERATOR_PRIORITY,

    /** No cellular voice service was detected on the selected SIM (Req 4.11). */
    NO_VOICE_SERVICE,

    /** No payment method strategies are available or capable (Req 4). */
    ALL_METHODS_UNAVAILABLE,
}
