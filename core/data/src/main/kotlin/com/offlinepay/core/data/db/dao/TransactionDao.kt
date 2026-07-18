package com.offlinepay.core.data.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.offlinepay.core.data.db.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for all transaction history operations.
 *
 * All queries are on the `transactions` table. A [PagingSource] is provided for
 * paginated history display (Req 8.4) while Flow-returning queries serve
 * dashboard summaries and filter results.
 *
 * Design reference: Section 9.3 (TransactionDao query descriptions)
 * Requirements: Req 8.1–8.18
 */
@Dao
interface TransactionDao {

    /** Insert a new transaction. Replaces on conflict (handles PENDING → update). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TransactionEntity)

    /** Update an existing transaction record. */
    @Update
    suspend fun update(entity: TransactionEntity)

    /** Update only status and updatedAt for a given id. */
    @Query("UPDATE transactions SET status = :status, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: Long)

    /** Returns all transactions ordered by timestamp descending as a Flow. */
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAll(): Flow<List<TransactionEntity>>

    /** Returns all transactions ordered by timestamp descending as a PagingSource (Req 8.4). */
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllPaged(): PagingSource<Int, TransactionEntity>

    /** Returns transactions matching status as a PagingSource, ordered by timestamp desc. */
    @Query("SELECT * FROM transactions WHERE status = :status ORDER BY timestamp DESC")
    fun getByStatusPaged(status: String): PagingSource<Int, TransactionEntity>

    /** Returns transactions matching status, ordered by timestamp desc. */
    @Query("SELECT * FROM transactions WHERE status = :status ORDER BY timestamp DESC")
    fun getByStatus(status: String): Flow<List<TransactionEntity>>

    /** Returns transactions in a date range (inclusive), ordered by timestamp desc. */
    @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :startMs AND :endMs ORDER BY timestamp DESC")
    fun getByDateRange(startMs: Long, endMs: Long): Flow<List<TransactionEntity>>

    /** Returns transactions with amount in range (inclusive), ordered by timestamp desc. */
    @Query("SELECT * FROM transactions WHERE amount BETWEEN :minPaise AND :maxPaise ORDER BY timestamp DESC")
    fun getByAmountRange(minPaise: Long, maxPaise: Long): Flow<List<TransactionEntity>>

    /** Returns transactions for a specific payee UPI ID, ordered by timestamp desc. */
    @Query("SELECT * FROM transactions WHERE payee_upi_id = :upiId ORDER BY timestamp DESC")
    fun getByMerchant(upiId: String): Flow<List<TransactionEntity>>

    /** Returns transactions for a specific payment method, ordered by timestamp desc. */
    @Query("SELECT * FROM transactions WHERE payment_method = :method ORDER BY timestamp DESC")
    fun getByPaymentMethod(method: String): Flow<List<TransactionEntity>>

    /**
     * Full-text search across payee_upi_id, payee_name, and merchant_name.
     * Case-insensitive LIKE search (Requirements: Req 8.6).
     */
    @Query("""
        SELECT * FROM transactions 
        WHERE payee_upi_id LIKE '%' || :query || '%' 
           OR payee_name LIKE '%' || :query || '%'
           OR merchant_name LIKE '%' || :query || '%'
        ORDER BY timestamp DESC
    """)
    fun search(query: String): Flow<List<TransactionEntity>>

    /** Returns the transaction with the given id, or null if not found. */
    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TransactionEntity?

    /** Returns transactions since [startOfDayMs] for the dashboard today summary. */
    @Query("SELECT * FROM transactions WHERE timestamp >= :startOfDayMs ORDER BY timestamp DESC")
    fun getToday(startOfDayMs: Long): Flow<List<TransactionEntity>>

    /** Returns transactions since [startOfMonthMs] for the dashboard month summary. */
    @Query("SELECT * FROM transactions WHERE timestamp >= :startOfMonthMs ORDER BY timestamp DESC")
    fun getThisMonth(startOfMonthMs: Long): Flow<List<TransactionEntity>>

    /** Returns transactions for export within a date range (one-shot, not Flow). */
    @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :startMs AND :endMs ORDER BY timestamp DESC")
    suspend fun getForExport(startMs: Long, endMs: Long): List<TransactionEntity>

    /** Permanently deletes a transaction by id. */
    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: String)

    /** Deletes all transactions older than [cutoffMs] (90-day retention). */
    @Query("DELETE FROM transactions WHERE timestamp < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long)

    /** Returns the count of all transactions. */
    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int
}
