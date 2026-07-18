package com.offlinepay.core.domain.usecase.merchant

import com.offlinepay.core.domain.model.MerchantProfile
import com.offlinepay.core.domain.repository.MerchantRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use case for retrieving the user's favourite merchants for the dashboard.
 *
 * Design reference: Section 10.2 (DashboardViewModel — favourite merchants)
 * Requirements: Req 17.5 (favourite merchants list)
 *
 * @param merchantRepository Injected via Hilt.
 */
class GetFavouriteMerchantsUseCase(
    private val merchantRepository: MerchantRepository,
) {
    /** Returns a [Flow] of [MerchantProfile]s marked as favourite, ordered by last seen. */
    operator fun invoke(): Flow<List<MerchantProfile>> = merchantRepository.getFavourites()
}
