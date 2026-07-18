package com.offlinepay.core.domain.payment

import com.offlinepay.core.domain.model.OperatorType
import com.offlinepay.core.domain.model.PaymentMethodType

/**
 * Plugin interface for registering a new offline payment method with the [StrategyRegistry].
 *
 * Each plugin is registered via Hilt `@IntoSet` multibinding so new payment methods
 * (NFC, SoundPay, future NPCI methods) can be added without changing the [RoutingEngine].
 *
 * Design reference: Section 4.1 (PaymentMethodPlugin interface), Section 4.3 (Pluggability)
 * Requirements: Req 4.7 (future offline payment methods registerable without engine changes)
 *
 * ### How to add a new payment method
 * 1. Create `NfcPaymentStrategy` implementing [PaymentMethodStrategy]
 * 2. Create `NfcPaymentPlugin` implementing [PaymentMethodPlugin]
 * 3. Bind it with `@IntoSet` in a Hilt module
 * 4. Done — RoutingEngine and StrategyRegistry require zero changes
 */
interface PaymentMethodPlugin {

    /** The [PaymentMethodType] this plugin registers. */
    fun methodType(): PaymentMethodType

    /** Factory method that creates the [PaymentMethodStrategy] for this plugin. */
    fun createStrategy(): PaymentMethodStrategy

    /**
     * Default routing priority for [operator].
     * Lower integer = higher priority.
     * Can be overridden by the user's [com.offlinepay.core.domain.model.RoutingPriorityConfig].
     */
    fun defaultPriority(operator: OperatorType): Int
}
