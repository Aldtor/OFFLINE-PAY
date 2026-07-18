package com.offlinepay.core.domain.usecase.merchant

import com.offlinepay.core.domain.model.MerchantProfile
import com.offlinepay.core.domain.repository.MerchantRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use case for retrieving the most recently seen unique merchants.
 *
 * Used by [DashboardViewModel] to populate the "Recent Merchants" list.
 * Returns a [Flow] that updates automatically when merchant data changes.
 *
 * Design reference: Section 3.3 (Use Cases)
 * Requirements: Req 17.4 (Recent Merchants list — last 5 unique)
 *
 * @param merchantRepository Injected via Hilt.
 */
class GetRecentMerchantsUseCase(
    private val merchantRepository: MerchantRepository,
) {

    /**
     * Returns a [Flow] of the [limit] most recently seen [MerchantProfile]s.
     *
     * Emits an empty list when no merchants have been seen (not an error).
     *
     * @param limit Maximum number of merchants to return. Defaults to 5 per Req 17.4.
     */
    operator fun invoke(limit: Int = 5): Flow<List<MerchantProfile>> =
        merchantRepository.getRecentMerchants(limit)
}
