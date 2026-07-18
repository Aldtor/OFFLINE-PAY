package com.offlinepay.core.domain.usecase.merchant

import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.domain.error.DomainError
import com.offlinepay.core.domain.model.MerchantProfile
import com.offlinepay.core.domain.model.PaymentParams
import com.offlinepay.core.domain.repository.MerchantRepository

/**
 * Use case for creating or updating a merchant profile after a payment completion.
 *
 * Called automatically by [UssdStrategy] and [Pay123Controller] on every
 * successful payment (Task 31.3).
 *
 * Design reference: Section 3.3 (Use Cases)
 * Requirements: Req 18 (merchant auto-upsert on payment completion)
 *
 * @param merchantRepository Injected via Hilt.
 */
class UpsertMerchantUseCase(
    private val merchantRepository: MerchantRepository,
) {
    /**
     * Creates or updates the merchant profile for [paymentParams.upiId].
     *
     * @param paymentParams Payment parameters containing the merchant's UPI ID and name.
     * @param timestampMs Epoch milliseconds of the payment completion.
     * @return [AppResult.Success] with the upserted [MerchantProfile].
     *         [AppResult.Failure] with [DomainError.StorageError.DatabaseError] on failure.
     */
    suspend operator fun invoke(
        paymentParams: PaymentParams,
        timestampMs: Long,
    ): AppResult<MerchantProfile, DomainError.StorageError> =
        merchantRepository.upsertFromPayment(paymentParams, timestampMs)
}
