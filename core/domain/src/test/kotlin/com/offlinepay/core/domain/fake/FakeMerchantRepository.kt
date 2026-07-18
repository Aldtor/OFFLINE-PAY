package com.offlinepay.core.domain.fake

import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.domain.error.DomainError
import com.offlinepay.core.domain.model.MerchantProfile
import com.offlinepay.core.domain.model.PaymentParams
import com.offlinepay.core.domain.repository.MerchantRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory fake [MerchantRepository] for unit tests.
 */
class FakeMerchantRepository : MerchantRepository {

    private val _merchants = MutableStateFlow<List<MerchantProfile>>(emptyList())
    val merchants: List<MerchantProfile> get() = _merchants.value

    // ── Primary interface methods ─────────────────────────────────────────────

    override suspend fun upsertMerchant(
        profile: MerchantProfile,
    ): AppResult<Unit, DomainError.StorageError> {
        _merchants.value = _merchants.value.filter {
            it.upiId != profile.upiId.lowercase()
        } + profile.copy(upiId = profile.upiId.lowercase())
        return AppResult.Success(Unit)
    }

    override suspend fun getMerchantByUpiId(
        upiId: String,
    ): AppResult<MerchantProfile?, DomainError.StorageError> =
        AppResult.Success(_merchants.value.find { it.upiId == upiId.lowercase() })

    override suspend fun toggleFavourite(
        upiId: String,
    ): AppResult<Unit, DomainError.StorageError> {
        _merchants.value = _merchants.value.map { m ->
            if (m.upiId == upiId.lowercase()) m.copy(isFavourite = !m.isFavourite) else m
        }
        return AppResult.Success(Unit)
    }

    override fun getRecentMerchants(limit: Int): Flow<List<MerchantProfile>> =
        _merchants.map { list -> list.sortedByDescending { it.lastSeenAt }.take(limit) }

    override fun getFavouriteMerchants(): Flow<List<MerchantProfile>> =
        _merchants.map { list -> list.filter { it.isFavourite } }

    // ── Legacy methods still required by the interface ────────────────────────

    override suspend fun upsertFromPayment(
        paymentParams: PaymentParams,
        timestampMs: Long,
    ): AppResult<MerchantProfile, DomainError.StorageError> {
        val existing = _merchants.value.find { it.upiId == paymentParams.upiId.lowercase() }
        val updated = if (existing != null) {
            existing.copy(
                name = paymentParams.payeeName ?: existing.name,
                lastSeenAt = timestampMs,
                transactionCount = existing.transactionCount + 1,
            )
        } else {
            MerchantProfile(
                id = java.util.UUID.randomUUID().toString(),
                upiId = paymentParams.upiId.lowercase(),
                name = paymentParams.payeeName ?: paymentParams.upiId,
                categoryCode = paymentParams.merchantCode,
                avatarColor = paymentParams.upiId.hashCode(),
                lastSeenAt = timestampMs,
                createdAt = timestampMs,
            )
        }
        _merchants.value = _merchants.value.filter { it.upiId != updated.upiId } + updated
        return AppResult.Success(updated)
    }

    override suspend fun toggleFavouriteById(
        merchantId: String,
    ): AppResult<Boolean, DomainError.StorageError> {
        val updated = _merchants.value.map { m ->
            if (m.id == merchantId) m.copy(isFavourite = !m.isFavourite) else m
        }
        _merchants.value = updated
        val newState = updated.find { it.id == merchantId }?.isFavourite ?: false
        return AppResult.Success(newState)
    }
}
