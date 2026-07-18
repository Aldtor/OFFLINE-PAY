package com.offlinepay.core.domain.usecase.payment

import com.offlinepay.core.domain.model.PaymentParams
import com.offlinepay.core.domain.model.PaymentResult
import com.offlinepay.core.domain.model.SimInfo
import com.offlinepay.core.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow

/**
 * Use case for initiating an offline payment using the selected method.
 *
 * Orchestrates the full payment flow:
 * 1. Get routing decision from [com.offlinepay.core.domain.payment.RoutingEngine]
 * 2. Persist a PENDING transaction record via [com.offlinepay.core.domain.usecase.transaction.SaveTransactionUseCase]
 * 3. Execute the selected [com.offlinepay.core.domain.payment.PaymentMethodStrategy]
 * 4. Handle fallback if the primary method fails (Design Section 4.2 executeWithFallback)
 * 5. Update transaction record with final result via [com.offlinepay.core.domain.usecase.transaction.UpdateTransactionStatusUseCase]
 *
 * Returns a [Flow] of [PaymentResult] that emits the final outcome.
 * Errors that occur before payment execution (e.g. no voice service, routing failure)
 * are emitted as [PaymentResult.Failure].
 *
 * Design reference: Section 4.2 (Routing + executeWithFallback algorithms)
 * Requirements: Req 4.1–4.11, Req 5, Req 6
 */
interface InitiatePaymentUseCase {

    /**
     * Initiates a payment and returns a [Flow] of [PaymentResult].
     *
     * The flow emits the final result (success, failure, pending, unknown).
     * The transaction record is persisted with PENDING status before execution
     * and updated with the final status after the flow completes.
     *
     * @param simInfo The active SIM to use for the payment.
     * @param params Payment parameters extracted from the QR code.
     * @param preferences Current user preferences (routing priority map and manual override).
     * @return A [Flow] emitting a single [PaymentResult].
     */
    suspend operator fun invoke(
        simInfo: SimInfo,
        params: PaymentParams,
        preferences: UserPreferences,
    ): Flow<PaymentResult>
}
