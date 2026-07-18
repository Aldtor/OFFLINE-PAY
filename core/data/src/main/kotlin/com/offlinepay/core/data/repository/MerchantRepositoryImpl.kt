package com.offlinepay.core.data.repository

import com.offlinepay.core.common.dispatchers.IoDispatcher
import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.common.result.appResultOf
import com.offlinepay.core.data.db.dao.MerchantDao
import com.offlinepay.core.data.db.mapper.EntityMapper.toDomain
import com.offlinepay.core.data.db.mapper.EntityMapper.toDomainMerchantList
import com.offlinepay.core.data.db.mapper.EntityMapper.toEntity
import com.offlinepay.core.domain.error.DomainError
import com.offlinepay.core.domain.model.MerchantProfile
import com.offlinepay.core.domain.model.PaymentParams
import com.offlinepay.core.domain.repository.MerchantRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production implementation of [MerchantRepository] backed by [MerchantDao].
 *
 * Upsert semantics:
 * - [insertIfNotExists] ignores conflicts on `upi_id` (unique index).
 * - [updateOnPayment] updates name/lastSeenAt/transactionCount for the existing row.
 * Together these implement DO UPDATE upsert semantics without a single REPLACE
 * query that would reset the `is_favourite` and `created_at` fields.
 *
 * Design reference: Section 9.5 (MerchantRepository)
 * Requirements: Req 18 (Merchant Card and Profile)
 */
@Singleton
class MerchantRepositoryImpl @Inject constructor(
    private val dao: MerchantDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : MerchantRepository {

    // ── Core interface methods ────────────────────────────────────────────────

    override suspend fun upsertMerchant(
        profile: MerchantProfile,
    ): AppResult<Unit, DomainError.StorageError> = withContext(ioDispatcher) {
        appResultOf(
            errorMapper = { DomainError.StorageError.DatabaseError("upsertMerchant", it.message ?: "unknown") },
        ) {
            val upiId = profile.upiId.lowercase()
            // Branch on existence: updateOnPayment increments transaction_count, so
            // running it after a fresh insert would double-count a brand-new merchant
            // (inserted with count=1, then bumped to 2). Only bump when the row already existed.
            if (dao.getByUpiId(upiId) != null) {
                dao.updateOnPayment(
                    upiId = upiId,
                    name = profile.name,
                    lastSeenAt = profile.lastSeenAt,
                )
            } else {
                dao.insertIfNotExists(profile.toEntity())
            }
        }
    }

    override suspend fun getMerchantByUpiId(
        upiId: String,
    ): AppResult<MerchantProfile?, DomainError.StorageError> = withContext(ioDispatcher) {
        appResultOf(
            errorMapper = { DomainError.StorageError.DatabaseError("getMerchantByUpiId", it.message ?: "unknown") },
        ) { dao.getByUpiId(upiId.lowercase())?.toDomain() }
    }

    override suspend fun toggleFavourite(
        upiId: String,
    ): AppResult<Unit, DomainError.StorageError> = withContext(ioDispatcher) {
        appResultOf(
            errorMapper = { DomainError.StorageError.DatabaseError("toggleFavourite", it.message ?: "unknown") },
        ) {
            val merchant = dao.getByUpiId(upiId.lowercase())
                ?: throw NoSuchElementException("Merchant not found for upiId: $upiId")
            dao.toggleFavourite(merchant.id)
        }
    }

    override fun getRecentMerchants(limit: Int): Flow<List<MerchantProfile>> =
        dao.getRecent(limit)
            .map { it.toDomainMerchantList() }
            .catch { emit(emptyList()) }
            .flowOn(ioDispatcher)

    override fun getFavouriteMerchants(): Flow<List<MerchantProfile>> =
        dao.getFavourites()
            .map { it.toDomainMerchantList() }
            .catch { emit(emptyList()) }
            .flowOn(ioDispatcher)

    override suspend fun toggleFavouriteById(
        merchantId: String,
    ): AppResult<Boolean, DomainError.StorageError> = withContext(ioDispatcher) {
        appResultOf(
            errorMapper = { DomainError.StorageError.DatabaseError("toggleFavouriteById", it.message ?: "unknown") },
        ) {
            dao.toggleFavourite(merchantId)
            val newState = dao.getFavouriteState(merchantId) ?: 0
            newState == 1
        }
    }

    // ── Legacy/convenience methods ────────────────────────────────────────────

    override suspend fun upsertFromPayment(
        paymentParams: PaymentParams,
        timestampMs: Long,
    ): AppResult<MerchantProfile, DomainError.StorageError> = withContext(ioDispatcher) {
        appResultOf(
            errorMapper = { DomainError.StorageError.DatabaseError("upsertFromPayment", it.message ?: "unknown") },
        ) {
            val upiId = paymentParams.upiId.lowercase()
            val existing = dao.getByUpiId(upiId)
            if (existing != null) {
                dao.updateOnPayment(
                    upiId = upiId,
                    name = paymentParams.payeeName ?: existing.name,
                    lastSeenAt = timestampMs,
                )
                dao.getByUpiId(upiId)?.toDomain()
                    ?: throw IllegalStateException("Merchant $upiId not found after update")
            } else {
                val profile = MerchantProfile(
                    id = UUID.randomUUID().toString(),
                    upiId = upiId,
                    name = paymentParams.payeeName ?: upiId,
                    categoryCode = paymentParams.merchantCode,
                    categoryName = null,
                    avatarColor = upiId.hashCode(),
                    isFavourite = false,
                    lastSeenAt = timestampMs,
                    transactionCount = 1,
                    createdAt = timestampMs,
                )
                dao.upsert(profile.toEntity())
                profile
            }
        }
    }

    override suspend fun getByUpiId(
        upiId: String,
    ): AppResult<MerchantProfile?, DomainError.StorageError> = getMerchantByUpiId(upiId)

    override fun getFavourites(): Flow<List<MerchantProfile>> = getFavouriteMerchants()

    override fun getRecent(limit: Int): Flow<List<MerchantProfile>> = getRecentMerchants(limit)
}
