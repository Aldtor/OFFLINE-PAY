package com.offlinepay.core.domain.payment

import com.offlinepay.core.domain.model.OperatorType
import com.offlinepay.core.domain.model.PaymentMethodType
import com.offlinepay.core.domain.model.PaymentParams
import com.offlinepay.core.domain.model.PaymentResult
import com.offlinepay.core.domain.model.SimInfo
import kotlinx.coroutines.flow.Flow

/**
 * Strategy interface for a single offline payment method (USSD or 123PAY).
 *
 * New payment methods (NFC, SoundPay, future NPCI methods) are added by implementing
 * this interface and registering via Hilt `@IntoSet` multibinding — no changes needed
 * to the [RoutingEngine].
 *
 * Design reference: Section 4.1 (Strategy Pattern), Section 4.3 (Pluggability)
 * Requirements: Req 4.7 (pluggable payment methods)
 */
interface PaymentMethodStrategy {

    /** The [PaymentMethodType] this strategy handles. */
    fun methodType(): PaymentMethodType

    /**
     * Returns true if this strategy is capable of handling the given [simInfo] and [params].
     *
     * Called by the [RoutingEngine] before selecting this strategy.
     * Implementations should check: voice service availability, operator support,
     * and any method-specific pre-conditions.
     */
    fun canHandle(simInfo: SimInfo, params: PaymentParams): Boolean

    /**
     * Executes the payment and returns a [Flow] of [PaymentResult].
     *
     * The flow emits intermediate states (USSD menu steps) and a terminal
     * [PaymentResult.Success], [PaymentResult.Failure], [PaymentResult.Pending],
     * or [PaymentResult.Unknown].
     *
     * Implementations must:
     * - Call `SaveTransactionUseCase(PENDING)` before initiating
     * - Never store or log UPI PINs (Req 5.8)
     * - Handle cancellation via coroutine cancellation
     */
    fun execute(simInfo: SimInfo, params: PaymentParams): Flow<PaymentResult>

    /**
     * Returns the priority of this strategy for [operator].
     * Lower integer = higher priority. The [RoutingEngine] sorts strategies by priority.
     *
     * Default priorities:
     * - USSD: priority 1 for Airtel/Vi/BSNL, priority 2 for Jio
     * - PAY123: priority 1 for Jio, priority 2 for others
     */
    fun priority(operator: OperatorType): Int
}
