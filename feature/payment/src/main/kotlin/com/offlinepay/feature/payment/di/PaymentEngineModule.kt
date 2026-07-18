package com.offlinepay.feature.payment.di

import com.offlinepay.core.domain.payment.PaymentMethodPlugin
import com.offlinepay.core.domain.payment.RoutingEngine
import com.offlinepay.core.domain.payment.StrategyRegistry
import com.offlinepay.feature.payment.engine.Pay123Plugin
import com.offlinepay.feature.payment.engine.RoutingEngineImpl
import com.offlinepay.feature.payment.engine.StrategyRegistryImpl
import com.offlinepay.feature.payment.engine.UssdPlugin
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/**
 * Hilt DI module that wires the Offline Payment Engine.
 *
 * - [StrategyRegistryImpl] is bound to [StrategyRegistry] as a singleton.
 * - [RoutingEngineImpl] is bound to [RoutingEngine] as a singleton.
 * - [UssdPlugin] and [Pay123Plugin] are contributed to the `Set<PaymentMethodPlugin>`
 *   multibinding so that [StrategyRegistryImpl] receives them all at construction time
 *   and auto-registers the corresponding strategies.
 *
 * Adding a new payment method only requires:
 * 1. Implementing [PaymentMethodStrategy] and [PaymentMethodPlugin] for the new method.
 * 2. Adding an `@Binds @IntoSet` binding here (or in a separate module).
 * No changes to [RoutingEngineImpl] or [StrategyRegistryImpl] are needed (Req 4.7).
 *
 * Design reference: Section 4.3 (Pluggability), Section 4.1 (DI wiring)
 * Requirements: Req 4.7 (future offline payment methods registerable without engine changes)
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PaymentEngineModule {

    @Binds
    @Singleton
    abstract fun bindStrategyRegistry(impl: StrategyRegistryImpl): StrategyRegistry

    @Binds
    @Singleton
    abstract fun bindRoutingEngine(impl: RoutingEngineImpl): RoutingEngine

    @Binds
    @IntoSet
    abstract fun bindUssdPlugin(plugin: UssdPlugin): PaymentMethodPlugin

    @Binds
    @IntoSet
    abstract fun bindPay123Plugin(plugin: Pay123Plugin): PaymentMethodPlugin
}
