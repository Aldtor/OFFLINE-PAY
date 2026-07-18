package com.offlinepay.core.domain.usecase.payment

import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.domain.error.DomainError
import com.offlinepay.core.domain.model.AppSettings
import com.offlinepay.core.domain.model.PaymentParams
import com.offlinepay.core.domain.model.RoutingDecision
import com.offlinepay.core.domain.model.SimInfo

/**
 * Use case for computing the routing decision for an offline payment.
 *
 * Delegates to the [com.offlinepay.core.domain.payment.RoutingEngine] implementation.
 * Called by [PaymentConfirmationViewModel] to determine which method to display,
 * and re-called when the user changes the selected SIM or overrides the method.
 *
 * Design reference: Section 4.2 (Routing Algorithm), Section 3.3 (Use Cases)
 * Requirements: Req 4.1–4.11
 */
fun interface GetRoutingDecisionUseCase {

    /**
     * Computes the [RoutingDecision] for [simInfo] and [params].
     *
     * @param simInfo The SIM to use for this payment.
     * @param params Payment parameters from the scanned QR.
     * @param settings Current app settings (routing priority, manual override).
     * @return [AppResult.Success] with a [RoutingDecision].
     *         The decision's [RoutingDecision.selectedMethod] may be null if no service available.
     */
    suspend operator fun invoke(
        simInfo: SimInfo,
        params: PaymentParams,
        settings: AppSettings,
    ): AppResult<RoutingDecision, DomainError>
}
