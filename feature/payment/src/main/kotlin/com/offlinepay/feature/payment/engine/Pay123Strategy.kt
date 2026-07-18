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
 * Strategy for UPI 123PAY IVR offline payments.
 *
 * Stub implementation — real IVR call logic is delivered in :feature:pay123 (task 25).
 * Emits a single [PaymentResult.Pending] because 123PAY results are always
 * user-reported and unverifiable by the system (Req 6.5).
 *
 * Priority rules (lower = higher priority):
 * - JIO → 1 (123PAY is the preferred method for Jio per Req 4.3)
 * - AIRTEL, VI, BSNL, OTHER → 2 (USSD is preferred; 123PAY acts as fallback)
 *
 * Design reference: Section 4.1 (Strategy Pattern), Section 4.3 (default routing priority)
 * Requirements: Req 4.3, Req 6 (123PAY payment flow)
 */
class Pay123Strategy : PaymentMethodStrategy {

    override fun methodType(): PaymentMethodType = PaymentMethodType.PAY123

    /**
     * 123PAY requires an active voice service channel for the IVR call.
     * Returns true when [simInfo.voiceServiceAvailable] is true.
     */
    override fun canHandle(simInfo: SimInfo, params: PaymentParams): Boolean =
        simInfo.voiceServiceAvailable

    /**
     * Initiates the 123PAY IVR call.
     *
     * Stub: emits [PaymentResult.Pending] with [PendingReason.USER_REPORTED].
     * Real implementation (ACTION_CALL to 123PAY IVR number) will be added in task 25.
     */
    override fun execute(simInfo: SimInfo, params: PaymentParams): Flow<PaymentResult> = flow {
        emit(
            PaymentResult.Pending(
                transactionId = "pending-pay123",
                method = PaymentMethodType.PAY123,
                reason = PendingReason.USER_REPORTED,
            )
        )
    }

    /**
     * Routing priority for 123PAY per operator.
     *
     * JIO is prioritised (priority 1) because *99# USSD has inconsistent support on Jio.
     * All other operators prefer USSD first and use 123PAY as a fallback (priority 2).
     */
    override fun priority(operator: OperatorType): Int = when (operator) {
        OperatorType.JIO   -> 1
        OperatorType.AIRTEL,
        OperatorType.VI,
        OperatorType.BSNL,
        OperatorType.OTHER -> 2
    }
}

/**
 * Hilt plugin that registers [Pay123Strategy] with the [com.offlinepay.core.domain.payment.StrategyRegistry].
 *
 * Bound via `@IntoSet` in [com.offlinepay.feature.payment.di.PaymentEngineModule] so the
 * engine picks it up automatically at startup — no changes required to [RoutingEngineImpl].
 *
 * Design reference: Section 4.3 (Pluggability)
 * Requirements: Req 4.7
 */
class Pay123Plugin @Inject constructor() : PaymentMethodPlugin {

    override fun methodType(): PaymentMethodType = PaymentMethodType.PAY123

    override fun createStrategy(): PaymentMethodStrategy = Pay123Strategy()

    /** Delegates to [Pay123Strategy.priority] to keep priority logic in one place. */
    override fun defaultPriority(operator: OperatorType): Int =
        Pay123Strategy().priority(operator)
}
