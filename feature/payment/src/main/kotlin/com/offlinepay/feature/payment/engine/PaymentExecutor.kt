package com.offlinepay.feature.payment.engine

import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.domain.error.DomainError
import com.offlinepay.core.domain.model.PaymentParams
import com.offlinepay.core.domain.model.PaymentResult
import com.offlinepay.core.domain.model.RoutingDecision
import com.offlinepay.core.domain.model.SimInfo
import com.offlinepay.core.domain.payment.PaymentMethodStrategy
import com.offlinepay.core.domain.payment.StrategyRegistry
import kotlinx.coroutines.flow.last
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Executes a payment using the strategy determined by [RoutingDecision], with
 * automatic fallback support.
 *
 * The algorithm:
 * 1. Build an ordered [List] of [PaymentMethodStrategy] instances from
 *    [RoutingDecision.selectedMethod] followed by [RoutingDecision.fallbackChain],
 *    skipping any type whose strategy is not registered.
 * 2. If the list is empty, return [AppResult.Failure] with [DomainError.PaymentError.RoutingFailed].
 * 3. For each strategy in order:
 *    a. Collect the terminal emission from [PaymentMethodStrategy.execute] via `Flow.last()`.
 *    b. If the result is [PaymentResult.Success], return immediately.
 *    c. If the result is [PaymentResult.Failure] and a next strategy exists,
 *       ask the caller via [onFallbackConfirm]; if the user declines, surface the
 *       failure result as a wrapped [AppResult.Success] (user's explicit decision).
 * 4. Return the last result once all strategies are exhausted.
 *
 * Design reference: Section 4.2 (Routing Algorithm), Section 4.5 (Fallback Chain)
 * Requirements: Req 4.8–4.10
 */
@Singleton
class PaymentExecutor @Inject constructor(
    private val registry: StrategyRegistry,
) {

    /**
     * Executes the payment described by [routingDecision] against [simInfo] / [params],
     * falling back through the chain when a method fails.
     *
     * @param routingDecision The routing output from [com.offlinepay.core.domain.payment.RoutingEngine].
     * @param simInfo         Active SIM information.
     * @param params          Payment parameters extracted from the QR code.
     * @param onFallbackConfirm Suspend callback invoked before attempting a fallback strategy.
     *   Receives the next [PaymentMethodStrategy] and returns `true` if the user consents
     *   to the fallback, `false` to stop and surface the current failure.
     * @return [AppResult.Success] wrapping the terminal [PaymentResult], or
     *   [AppResult.Failure] with [DomainError.PaymentError.RoutingFailed] when no strategy
     *   is available at all.
     */
    suspend fun executeWithFallback(
        routingDecision: RoutingDecision,
        simInfo: SimInfo,
        params: PaymentParams,
        onFallbackConfirm: suspend (PaymentMethodStrategy) -> Boolean,
    ): AppResult<PaymentResult, DomainError> {
        // Build the ordered list of strategies, skipping any unregistered types.
        val methods: List<PaymentMethodStrategy> = buildList {
            routingDecision.selectedMethod
                ?.let { type -> registry.get(type) }
                ?.let { add(it) }
            routingDecision.fallbackChain.forEach { type ->
                registry.get(type)?.let { add(it) }
            }
        }

        if (methods.isEmpty()) {
            return AppResult.Failure(
                DomainError.PaymentError.RoutingFailed(reason = "No payment method strategies available"),
            )
        }

        var lastResult: PaymentResult? = null
        for (i in methods.indices) {
            val strategy = methods[i]
            val result = strategy.execute(simInfo, params).last()
            lastResult = result

            if (result is PaymentResult.Success) {
                return AppResult.Success(result)
            }

            if (result is PaymentResult.Failure && i < methods.lastIndex) {
                val confirmed = onFallbackConfirm(methods[i + 1])
                if (!confirmed) {
                    // User declined the fallback — surface the failure as an acknowledged result.
                    return AppResult.Success(result)
                }
            }
        }

        // All strategies exhausted; return the last result (Failure / Pending / Unknown).
        return AppResult.Success(lastResult!!)
    }
}
