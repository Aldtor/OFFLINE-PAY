package com.offlinepay.core.domain.repository

import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.domain.error.DomainError
import com.offlinepay.core.domain.model.TransactionFilters
import com.offlinepay.core.domain.model.TransactionRecord
import com.offlinepay.core.domain.model.TransactionStatus
import com.offlinepay.core.domain.model.TransactionSummary
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for all transaction history operations.
 *
 * Implemented by `TransactionRepositoryImpl` in `:core:data`.
 * The domain layer depends only on this interface — never on the Room implementation.
 *
 * Note: This module is pure Kotlin with zero Android dependencies. Therefore,
 * `getTransactionsPaged` returns a `Flow<List<TransactionRecord>>` rather than
 * `PagingData<TransactionRecord>`. The data layer wraps this to a `PagingSource` /
 * `Pager` internally, exposing `PagingData` only at the ViewModel boundary.
 *
 * Design reference: Section 9.5 (TransactionRepository interface)
 * Requirements: Req 8 (Transaction History)
 */
interface TransactionRepository {

    /**
     * Persists a new transaction record.
     * Called immediately before initiating a payment, with [TransactionStatus.PENDING].
     *
     * Requirements: Req 8.1 (persist every payment attempt)
     */
    suspend fun saveTransaction(
        record: TransactionRecord,
    ): AppResult<Unit, DomainError.StorageError>

    /**
     * Updates the status of an existing transaction identified by [id].
     *
     * Requirements: Req 8 (status update after USSD/123PAY result)
     */
    suspend fun updateTransactionStatus(
        id: String,
        status: TransactionStatus,
    ): AppResult<Unit, DomainError.StorageError>

    /**
     * Looks up a single transaction by its UUID.
     * Returns [AppResult.Failure] with [DomainError.StorageError.DatabaseError] if not found.
     *
     * Requirements: Req 8.14 (transaction receipt screen)
     */
    suspend fun getTransactionById(
        id: String,
    ): AppResult<TransactionRecord, DomainError.StorageError>

    /**
     * Returns a [Flow] of all transactions matching [filters], in reverse chronological order.
     * Emits a new list whenever the underlying data changes.
     *
     * The data layer wraps this into a `PagingData` stream at the ViewModel boundary;
     * the domain layer stays free of Android Paging dependencies.
     *
     * Requirements: Req 8.4 (reverse chronological order), Req 8.6–8.11 (filters)
     */
    fun getTransactionsPaged(filters: TransactionFilters): Flow<List<TransactionRecord>>

    /**
     * Returns all transactions matching [filters] as a one-shot list for PDF/CSV export.
     *
     * Requirements: Req 8.15–8.17 (PDF/CSV export)
     */
    suspend fun getTransactionsForExport(
        filters: TransactionFilters,
    ): AppResult<List<TransactionRecord>, DomainError.StorageError>

    /**
     * Permanently deletes a transaction record after user confirmation.
     *
     * Requirements: Req 8.13
     */
    suspend fun deleteTransaction(id: String): AppResult<Unit, DomainError.StorageError>

    /**
     * Returns an aggregated [TransactionSummary] for transactions initiated today
     * (since 00:00 local time).
     *
     * Requirements: Req 17.1 (today's payment count for dashboard)
     */
    suspend fun getTodayTransactionSummary(): AppResult<TransactionSummary, DomainError.StorageError>

    /**
     * Returns an aggregated [TransactionSummary] for transactions initiated this
     * calendar month (since day 1 of current month).
     *
     * Requirements: Req 17.2 (this month's payment count for dashboard)
     */
    suspend fun getMonthTransactionSummary(): AppResult<TransactionSummary, DomainError.StorageError>

    /**
     * Deletes all transaction records with a timestamp older than [timestampMs].
     * Returns the number of deleted rows.
     * Called by the 90-day retention worker.
     *
     * Requirements: Req 8.12 (90-day retention)
     */
    suspend fun deleteTransactionsOlderThan(timestampMs: Long): AppResult<Int, DomainError.StorageError>

    // ── Legacy / convenience methods kept for use-case compatibility ──────────

    /**
     * Alias for [saveTransaction]. Retained for use-case layer compatibility.
     */
    suspend fun save(transaction: TransactionRecord): AppResult<Unit, DomainError.StorageError> =
        saveTransaction(transaction)

    /**
     * Alias for [updateTransactionStatus]. Retained for use-case layer compatibility.
     */
    suspend fun updateStatus(
        id: String,
        status: TransactionStatus,
        updatedAt: Long,
    ): AppResult<Unit, DomainError.StorageError> = updateTransactionStatus(id, status)

    /**
     * Alias for [getTransactionsPaged]. Retained for use-case layer compatibility.
     */
    fun getAll(filters: TransactionFilters): Flow<List<TransactionRecord>> =
        getTransactionsPaged(filters)

    /**
     * Alias for [getTransactionById]. Retained for use-case layer compatibility.
     */
    suspend fun getById(id: String): AppResult<TransactionRecord, DomainError.StorageError> =
        getTransactionById(id)

    /**
     * Returns a [Flow] of today's transactions (since 00:00 local time).
     * For a summary (count + total), use [getTodayTransactionSummary].
     *
     * Requirements: Req 17.1
     */
    fun getToday(): Flow<List<TransactionRecord>>

    /**
     * Returns a [Flow] of this month's transactions (since day 1 of current month).
     * For a summary (count + total), use [getMonthTransactionSummary].
     *
     * Requirements: Req 17.2
     */
    fun getThisMonth(): Flow<List<TransactionRecord>>

    /**
     * Returns all transactions within [startMs]..[endMs] for export.
     * Prefer [getTransactionsForExport] with [TransactionFilters] for new callers.
     *
     * Requirements: Req 8.15–8.17 (PDF/CSV export)
     */
    suspend fun getForExport(
        startMs: Long,
        endMs: Long,
    ): AppResult<List<TransactionRecord>, DomainError.StorageError>

    /**
     * Alias for [deleteTransaction]. Retained for use-case layer compatibility.
     */
    suspend fun delete(id: String): AppResult<Unit, DomainError.StorageError> =
        deleteTransaction(id)

    /**
     * Deletes all transaction records older than [cutoffMs].
     * Returns [AppResult.Success(Unit)] on completion (use [deleteTransactionsOlderThan]
     * when the count of deleted rows is needed).
     *
     * Requirements: Req 8.12 (90-day retention)
     */
    suspend fun deleteOlderThan(cutoffMs: Long): AppResult<Unit, DomainError.StorageError>
}
