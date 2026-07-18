package com.offlinepay.feature.payment.engine

import com.offlinepay.core.domain.model.OperatorType
import com.offlinepay.core.domain.model.PaymentMethodType
import com.offlinepay.core.domain.payment.PaymentMethodPlugin
import com.offlinepay.core.domain.payment.PaymentMethodStrategy
import com.offlinepay.core.domain.payment.StrategyRegistry
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default implementation of [StrategyRegistry].
 *
 * At construction time Hilt injects the full `Set<PaymentMethodPlugin>` (all
 * `@IntoSet`-bound plugins) and the `init` block auto-registers each one.
 * The backing store is a [ConcurrentHashMap] so reads and writes are thread-safe
 * without locking the entire map.
 *
 * Design reference: Section 4.1 (StrategyRegistry class)
 * Requirements: Req 4.7 (pluggable, registerable without engine changes)
 */
@Singleton
class StrategyRegistryImpl @Inject constructor(
    private val plugins: @JvmSuppressWildcards Set<PaymentMethodPlugin>
) : StrategyRegistry {

    private val map = ConcurrentHashMap<PaymentMethodType, PaymentMethodStrategy>()

    init {
        plugins.forEach { register(it) }
    }

    override fun register(plugin: PaymentMethodPlugin) {
        map[plugin.methodType()] = plugin.createStrategy()
    }

    override fun deregister(methodType: PaymentMethodType) {
        map.remove(methodType)
    }

    override fun getStrategiesForOperator(operator: OperatorType): List<PaymentMethodStrategy> =
        map.values.sortedBy { it.priority(operator) }

    override fun getAll(): List<PaymentMethodStrategy> =
        map.values.toList()

    override fun get(methodType: PaymentMethodType): PaymentMethodStrategy? =
        map[methodType]
}
