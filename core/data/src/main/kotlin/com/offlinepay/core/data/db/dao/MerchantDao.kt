package com.offlinepay.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.offlinepay.core.data.db.entity.MerchantEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for merchant profile operations.
 *
 * Design reference: Section 9.3 (MerchantDao query descriptions)
 * Requirements: Req 18 (Merchant Card and Profile)
 */
@Dao
interface MerchantDao {

    /**
     * Inserts a merchant, or replaces on conflict by upi_id.
     * Used for initial creation only.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNotExists(entity: MerchantEntity)

    /** Inserts or fully replaces a merchant record. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MerchantEntity)

    /** Updates name, lastSeenAt, and transactionCount for an existing merchant. */
    @Query("""
        UPDATE merchants 
        SET name = :name, last_seen_at = :lastSeenAt, transaction_count = transaction_count + 1
        WHERE upi_id = :upiId
    """)
    suspend fun updateOnPayment(upiId: String, name: String, lastSeenAt: Long)

    /** Returns merchant by UPI ID (case-sensitive — normalise to lowercase before calling). */
    @Query("SELECT * FROM merchants WHERE upi_id = :upiId LIMIT 1")
    suspend fun getByUpiId(upiId: String): MerchantEntity?

    /** Returns all favourite merchants ordered by last seen (most recent first). */
    @Query("SELECT * FROM merchants WHERE is_favourite = 1 ORDER BY last_seen_at DESC")
    fun getFavourites(): Flow<List<MerchantEntity>>

    /** Returns the [limit] most recently seen merchants. */
    @Query("SELECT * FROM merchants ORDER BY last_seen_at DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<MerchantEntity>>

    /** Toggles the favourite flag for a merchant. */
    @Query("UPDATE merchants SET is_favourite = CASE WHEN is_favourite = 1 THEN 0 ELSE 1 END WHERE id = :id")
    suspend fun toggleFavourite(id: String)

    /** Returns the current favourite state for a merchant. */
    @Query("SELECT is_favourite FROM merchants WHERE id = :id LIMIT 1")
    suspend fun getFavouriteState(id: String): Int?

    /** Returns the count of transactions with a merchant in the last [sinceMs] milliseconds. */
    @Query("SELECT transaction_count FROM merchants WHERE upi_id = :upiId LIMIT 1")
    suspend fun getTransactionCount(upiId: String): Int?

    /** Increments the transaction_count for a merchant by 1. */
    @Query("UPDATE merchants SET transaction_count = transaction_count + 1 WHERE id = :id")
    suspend fun incrementTransactionCount(id: String)
}
