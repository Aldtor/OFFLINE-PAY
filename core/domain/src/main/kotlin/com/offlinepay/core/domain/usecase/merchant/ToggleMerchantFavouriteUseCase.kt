package com.offlinepay.core.domain.usecase.merchant

import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.domain.error.DomainError
import com.offlinepay.core.domain.repository.MerchantRepository

/**
 * Use case for toggling the favourite state of a merchant by their UPI ID.
 *
 * The new state is persisted immediately (Req 18.5).
 * The UI observes [GetRecentMerchantsUseCase] and favourite merchant flows
 * which automatically emit updated lists after the toggle.
 *
 * Design reference: Section 3.3 (Use Cases)
 * Requirements: Req 18.5 (favourite toggle persisted)
 *
 * @param repository Injected via Hilt.
 */
class ToggleMerchantFavouriteUseCase(
    private val repository: MerchantRepository,
) {

    /**
     * Toggles the favourite state for the merchant identified by [upiId].
     *
     * @param upiId UPI ID of the merchant to toggle.
     * @return [AppResult.Success] with [Unit] on success.
     *         [AppResult.Failure] with [DomainError.StorageError.DatabaseError] on failure.
     */
    suspend operator fun invoke(
        upiId: String,
    ): AppResult<Unit, DomainError.StorageError> =
        repository.toggleFavourite(upiId)
}
