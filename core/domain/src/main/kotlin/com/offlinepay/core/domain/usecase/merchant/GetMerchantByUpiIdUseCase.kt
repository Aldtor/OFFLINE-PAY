package com.offlinepay.core.domain.usecase.merchant

import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.domain.error.DomainError
import com.offlinepay.core.domain.model.MerchantProfile

/**
 * Use case for looking up a merchant profile by UPI ID.
 *
 * Used by the payment confirmation screen to pre-populate the merchant card
 * if the merchant has been seen before.
 *
 * Design reference: Section 3.3 (Use Cases)
 * Requirements: Req 18 (Merchant Card)
 */
fun interface GetMerchantByUpiIdUseCase {

    /**
     * Returns the [MerchantProfile] for [upiId], or null if not previously seen.
     *
     * A null result is not an error — it simply means no profile exists yet.
     *
     * @return [AppResult.Success] with [MerchantProfile] or null.
     *         [AppResult.Failure] with [DomainError.StorageError.DatabaseError] on DB failure.
     */
    suspend operator fun invoke(
        upiId: String,
    ): AppResult<MerchantProfile?, DomainError.StorageError>
}
