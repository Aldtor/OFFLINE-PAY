package com.offlinepay.core.domain.repository

import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.domain.error.DomainError
import com.offlinepay.core.domain.model.MerchantProfile
import com.offlinepay.core.domain.model.PaymentParams
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for merchant profile management.
 *
 * Merchants are automatically upserted on every payment completion (Req 18).
 * Implemented by `MerchantRepositoryImpl` in `:core:data`.
 *
 * Design reference: Section 9.5 (MerchantRepository interface)
 * Requirements: Req 18 (Merchant Card and Profile)
 */
interface MerchantRepository {

    /**
     * Creates or updates a merchant profile from a fully-populated [MerchantProfile].
     *
     * - If no merchant with [MerchantProfile.upiId] exists: inserts the profile.
     * - If a merchant already exists: updates `name`, `lastSeenAt`, and `transactionCount`.
     *
     * Requirements: Req 18 (merchant upsert on payment completion)
     */
    suspend fun upsertMerchant(
        profile: MerchantProfile,
    ): AppResult<Unit, DomainError.StorageError>

    /**
     * Looks up a merchant profile by UPI ID (case-insensitive).
     * Returns `null` data (not an error) if no merchant with that UPI ID exists.
     *
     * Requirements: Req 18.2 (merchant profile retrieval)
     */
    suspend fun getMerchantByUpiId(
        upiId: String,
    ): AppResult<MerchantProfile?, DomainError.StorageError>

    /**
     * Toggles the favourite flag for the merchant with [upiId].
     *
     * Requirements: Req 18.5 (favourite toggle persisted)
     */
    suspend fun toggleFavourite(
        upiId: String,
    ): AppResult<Unit, DomainError.StorageError>

    /**
     * Returns a [Flow] of the [limit] most recently seen unique merchants.
     * Emits a new list whenever the underlying data changes.
     *
     * Requirements: Req 17.4 (recent merchants on dashboard, last 5 unique)
     */
    fun getRecentMerchants(limit: Int = 5): Flow<List<MerchantProfile>>

    /**
     * Returns a [Flow] of merchants the user has marked as favourites,
     * ordered by most recently seen.
     * Emits a new list whenever the underlying data changes.
     *
     * Requirements: Req 17.5 (favourite merchants on dashboard)
     */
    fun getFavouriteMerchants(): Flow<List<MerchantProfile>>

    // ── Legacy / convenience methods kept for use-case layer compatibility ────

    /**
     * Creates or updates a merchant profile derived from payment parameters.
     * Prefer [upsertMerchant] for new callers.
     *
     * - If no merchant with [PaymentParams.upiId] exists: creates a new profile.
     * - If a merchant already exists: updates `name`, `lastSeenAt`, increments `transactionCount`.
     *
     * Requirements: Req 18 (merchant auto-upsert on payment completion)
     */
    suspend fun upsertFromPayment(
        paymentParams: PaymentParams,
        timestampMs: Long,
    ): AppResult<MerchantProfile, DomainError.StorageError>

    /**
     * Alias for [getMerchantByUpiId]. Retained for use-case layer compatibility.
     */
    suspend fun getByUpiId(
        upiId: String,
    ): AppResult<MerchantProfile?, DomainError.StorageError> =
        getMerchantByUpiId(upiId)

    /**
     * Alias for [getFavouriteMerchants]. Retained for use-case layer compatibility.
     */
    fun getFavourites(): Flow<List<MerchantProfile>> = getFavouriteMerchants()

    /**
     * Alias for [getRecentMerchants]. Retained for use-case layer compatibility.
     */
    fun getRecent(limit: Int = 5): Flow<List<MerchantProfile>> = getRecentMerchants(limit)

    /**
     * Toggles the favourite state by merchant ID and returns the new boolean state.
     * Prefer [toggleFavourite] (by UPI ID) for new callers.
     *
     * Requirements: Req 18.5 (favourite toggle persisted)
     */
    suspend fun toggleFavouriteById(
        merchantId: String,
    ): AppResult<Boolean, DomainError.StorageError>
}
