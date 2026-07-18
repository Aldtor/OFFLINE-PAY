package com.offlinepay.core.domain.payment

import com.offlinepay.core.domain.model.OperatorType
import com.offlinepay.core.domain.model.PaymentMethodType

/**
 * Registry that manages all registered [PaymentMethodStrategy] instances.
 *
 * At application startup, Hilt injects the full `Set<PaymentMethodPlugin>` and the
 * registry auto-registers all plugins. The [RoutingEngine] queries this registry to
 * obtain the ordered list of strategies for a given operator.
 *
 * Design reference: Section 4.1 (StrategyRegistry class), Section 4.3 (Pluggability)
 * Requirements: Req 4.7 (pluggable, registerable without engine changes)
 */
interface StrategyRegistry {

    /**
     * Registers a [PaymentMethodPlugin] and its associated strategy into the registry.
     * Called at startup by the Hilt-injected plugin set, and can be called at runtime
     * to dynamically add new payment methods.
     */
    fun register(plugin: PaymentMethodPlugin)

    /**
     * Removes the strategy associated with [methodType] from the registry.
     * No-op if the method type is not currently registered.
     */
    fun deregister(methodType: PaymentMethodType)

    /**
     * Returns all registered strategies for [operator], sorted by their
     * [PaymentMethodStrategy.priority] for that operator (ascending — lower = higher priority).
     */
    fun getStrategiesForOperator(operator: OperatorType): List<PaymentMethodStrategy>

    /**
     * Returns all registered strategies across all operators.
     */
    fun getAll(): List<PaymentMethodStrategy>

    /**
     * Returns the strategy for [methodType], or null if not registered.
     */
    fun get(methodType: PaymentMethodType): PaymentMethodStrategy?
}
