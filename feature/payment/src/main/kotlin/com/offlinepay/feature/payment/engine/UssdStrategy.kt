package com.offlinepay.feature.payment.engine

import com.offlinepay.core.domain.model.OperatorType
import com.offlinepay.core.domain.model.PaymentMethodType
import com.offlinepay.core.domain.model.PaymentParams
import com.offlinepay.core.domain.model.PaymentResult
import com.offlinepay.core.domain.model.PendingReason
import com.offlinepay.core.domain.model.SimInfo
import com.offlinepay.core.domain.payment.PaymentMethodPlugin
import com.offlinepay.core.domain.payment.PaymentMethodStrategy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Strategy for USSD *99# offline payments.
 *
 * Stub implementation — real USSD dialling logic is delivered in :feature:ussd (task 24).
 * Emits a single [PaymentResult.Pending] to signal that the USSD session has been
 * initiated and is awaiting a system-level callback.
 *
 * Priority rules (lower = higher priority):
 * - AIRTEL, VI, BSNL, OTHER → 1 (USSD is the preferred method)
 * - JIO → 2 (123PAY is preferred for Jio per Req 4.3)
 *
 * Design reference: Section 4.1 (Strategy Pattern), Section 4.3 (default routing priority)
 * Requirements: Req 4.3, Req 5 (USSD payment flow)
 */
class UssdStrategy : PaymentMethodStrategy {

    override fun methodType(): PaymentMethodType = PaymentMethodType.USSD

    /**
     * USSD requires an active voice service channel.
     * Returns true when [simInfo.voiceServiceAvailable] is true.
     */
    override fun canHandle(simInfo: SimInfo, params: PaymentParams): Boolean =
        simInfo.voiceServiceAvailable

    /**
     * Initiates the USSD session.
     *
     * Stub: emits [PaymentResult.Pending] with [PendingReason.USER_REPORTED].
     * Real implementation (TelephonyManager.sendUssdRequest) will be added in task 24.
     */
    override fun execute(simInfo: SimInfo, params: PaymentParams): Flow<PaymentResult> = flow {
        emit(
            PaymentResult.Pending(
                transactionId = "pending-ussd",
                method = PaymentMethodType.USSD,
                reason = PendingReason.USER_REPORTED,
            )
        )
    }

    /**
     * Routing priority for USSD per operator.
     *
     * JIO has inconsistent *99# support so it is de-prioritised (priority 2).
     * All other operators default to priority 1 (highest).
     */
    override fun priority(operator: OperatorType): Int = when (operator) {
        OperatorType.JIO   -> 2
        OperatorType.AIRTEL,
        OperatorType.VI,
        OperatorType.BSNL,
        OperatorType.OTHER -> 1
    }
}

/**
 * Hilt plugin that registers [UssdStrategy] with the [com.offlinepay.core.domain.payment.StrategyRegistry].
 *
 * Bound via `@IntoSet` in [com.offlinepay.feature.payment.di.PaymentEngineModule] so the
 * engine picks it up automatically at startup — no changes required to [RoutingEngineImpl].
 *
 * Design reference: Section 4.3 (Pluggability)
 * Requirements: Req 4.7
 */
class UssdPlugin @Inject constructor() : PaymentMethodPlugin {

    override fun methodType(): PaymentMethodType = PaymentMethodType.USSD

    override fun createStrategy(): PaymentMethodStrategy = UssdStrategy()

    /** Delegates to [UssdStrategy.priority] to keep priority logic in one place. */
    override fun defaultPriority(operator: OperatorType): Int =
        UssdStrategy().priority(operator)
}
