package com.offlinepay.core.data.repository

import com.offlinepay.core.common.dispatchers.IoDispatcher
import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.common.result.appResultOf
import com.offlinepay.core.common.time.DateTimeUtils
import com.offlinepay.core.data.db.dao.TransactionDao
import com.offlinepay.core.data.db.mapper.EntityMapper.toDomain
import com.offlinepay.core.data.db.mapper.EntityMapper.toDomainList
import com.offlinepay.core.data.db.mapper.EntityMapper.toEntity
import com.offlinepay.core.domain.error.DomainError
import com.offlinepay.core.domain.model.TransactionFilters
import com.offlinepay.core.domain.model.TransactionRecord
import com.offlinepay.core.domain.model.TransactionStatus
import com.offlinepay.core.domain.model.TransactionSummary
import com.offlinepay.core.domain.repository.TransactionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production implementation of [TransactionRepository] backed by [TransactionDao].
 *
 * All operations are dispatched to the IO dispatcher. Room exceptions are caught
 * and wrapped in [AppResult.Failure] with [DomainError.StorageError.DatabaseError].
 *
 * Design reference: Section 9.5 (TransactionRepository)
 * Requirements: Req 8
 */
@Singleton
class TransactionRepositoryImpl @Inject constructor(
    private val dao: TransactionDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : TransactionRepository {

    // ── Core interface methods (new API) ──────────────────────────────────────

    override suspend fun saveTransaction(
        record: TransactionRecord,
    ): AppResult<Unit, DomainError.StorageError> = withContext(ioDispatcher) {
        appResultOf(
            errorMapper = { DomainError.StorageError.DatabaseError("saveTransaction", it.message ?: "unknown") },
        ) { dao.insert(record.toEntity()) }
    }

    override suspend fun updateTransactionStatus(
        id: String,
        status: TransactionStatus,
    ): AppResult<Unit, DomainError.StorageError> = withContext(ioDispatcher) {
        appResultOf(
            errorMapper = { DomainError.StorageError.DatabaseError("updateTransactionStatus", it.message ?: "unknown") },
        ) { dao.updateStatus(id, status.name, System.currentTimeMillis()) }
    }

    override suspend fun getTransactionById(
        id: String,
    ): AppResult<TransactionRecord, DomainError.StorageError> = withContext(ioDispatcher) {
        appResultOf(
            errorMapper = { DomainError.StorageError.DatabaseError("getTransactionById", it.message ?: "unknown") },
        ) {
            dao.getById(id)?.toDomain()
                ?: throw NoSuchElementException("Transaction not found: $id")
        }
    }

    override fun getTransactionsPaged(filters: TransactionFilters): Flow<List<TransactionRecord>> {
        return buildFilteredFlow(filters)
    }

    override suspend fun getTransactionsForExport(
        filters: TransactionFilters,
    ): AppResult<List<TransactionRecord>, DomainError.StorageError> = withContext(ioDispatcher) {
        appResultOf(
            errorMapper = { DomainError.StorageError.DatabaseError("getTransactionsForExport", it.message ?: "unknown") },
        ) {
            val startMs = filters.startDateMs ?: 0L
            val endMs = filters.endDateMs ?: Long.MAX_VALUE
            dao.getForExport(startMs, endMs).toDomainList()
        }
    }

    override suspend fun deleteTransaction(
        id: String,
    ): AppResult<Unit, DomainError.StorageError> = withContext(ioDispatcher) {
        appResultOf(
            errorMapper = { DomainError.StorageError.DatabaseError("deleteTransaction", it.message ?: "unknown") },
        ) { dao.delete(id) }
    }

    override suspend fun getTodayTransactionSummary(): AppResult<TransactionSummary, DomainError.StorageError> =
        withContext(ioDispatcher) {
            appResultOf(
                errorMapper = { DomainError.StorageError.DatabaseError("getTodayTransactionSummary", it.message ?: "unknown") },
            ) {
                val transactions = dao.getForExport(DateTimeUtils.startOfDayMs(), Long.MAX_VALUE)
                TransactionSummary(
                    count = transactions.size,
                    totalAmountPaise = transactions.sumOf { it.amount },
                )
            }
        }

    override suspend fun getMonthTransactionSummary(): AppResult<TransactionSummary, DomainError.StorageError> =
        withContext(ioDispatcher) {
            appResultOf(
                errorMapper = { DomainError.StorageError.DatabaseError("getMonthTransactionSummary", it.message ?: "unknown") },
            ) {
                val transactions = dao.getForExport(DateTimeUtils.startOfMonthMs(), Long.MAX_VALUE)
                TransactionSummary(
                    count = transactions.size,
                    totalAmountPaise = transactions.sumOf { it.amount },
                )
            }
        }

    override suspend fun deleteTransactionsOlderThan(
        timestampMs: Long,
    ): AppResult<Int, DomainError.StorageError> = withContext(ioDispatcher) {
        appResultOf(
            errorMapper = { DomainError.StorageError.DatabaseError("deleteTransactionsOlderThan", it.message ?: "unknown") },
        ) {
            // count before deletion, then delete
            val count = dao.count()
            dao.deleteOlderThan(timestampMs)
            val countAfter = dao.count()
            count - countAfter
        }
    }

    // ── Legacy/convenience methods (alias implementations) ────────────────────

    override suspend fun save(
        transaction: TransactionRecord,
    ): AppResult<Unit, DomainError.StorageError> = saveTransaction(transaction)

    override suspend fun updateStatus(
        id: String,
        status: TransactionStatus,
        updatedAt: Long,
    ): AppResult<Unit, DomainError.StorageError> = withContext(ioDispatcher) {
        appResultOf(
            errorMapper = { DomainError.StorageError.DatabaseError("updateStatus", it.message ?: "unknown") },
        ) { dao.updateStatus(id, status.name, updatedAt) }
    }

    override fun getAll(filters: TransactionFilters): Flow<List<TransactionRecord>> =
        buildFilteredFlow(filters)

    override suspend fun getById(
        id: String,
    ): AppResult<TransactionRecord, DomainError.StorageError> = getTransactionById(id)

    override fun getToday(): Flow<List<TransactionRecord>> =
        dao.getToday(DateTimeUtils.startOfDayMs())
            .map { it.toDomainList() }
            .catch { emit(emptyList()) }
            .flowOn(ioDispatcher)

    override fun getThisMonth(): Flow<List<TransactionRecord>> =
        dao.getThisMonth(DateTimeUtils.startOfMonthMs())
            .map { it.toDomainList() }
            .catch { emit(emptyList()) }
            .flowOn(ioDispatcher)

    override suspend fun getForExport(
        startMs: Long,
        endMs: Long,
    ): AppResult<List<TransactionRecord>, DomainError.StorageError> = withContext(ioDispatcher) {
        appResultOf(
            errorMapper = { DomainError.StorageError.DatabaseError("getForExport", it.message ?: "unknown") },
        ) { dao.getForExport(startMs, endMs).toDomainList() }
    }

    override suspend fun delete(
        id: String,
    ): AppResult<Unit, DomainError.StorageError> = deleteTransaction(id)

    override suspend fun deleteOlderThan(
        cutoffMs: Long,
    ): AppResult<Unit, DomainError.StorageError> = withContext(ioDispatcher) {
        appResultOf(
            errorMapper = { DomainError.StorageError.DatabaseError("deleteOlderThan", it.message ?: "unknown") },
        ) { dao.deleteOlderThan(cutoffMs) }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Builds a filtered [Flow] from the provided [TransactionFilters].
     *
     * A single DAO query is chosen (by precedence) to serve as the index-optimised
     * base stream; then **every** active filter is re-applied in-memory so that
     * multiple filters combine with AND logic (Req 8.6–8.11). Re-applying the
     * dimension already covered by the base query is idempotent and harmless.
     */
    private fun buildFilteredFlow(filters: TransactionFilters): Flow<List<TransactionRecord>> {
        val searchQuery = filters.searchQuery
        val status = filters.status
        val paymentMethod = filters.paymentMethod
        val startMs = filters.startDateMs
        val endMs = filters.endDateMs
        val minAmount = filters.minAmountPaise
        val maxAmount = filters.maxAmountPaise
        val merchantName = filters.merchantName

        val baseFlow = when {
            searchQuery != null -> dao.search(searchQuery)
            status != null -> dao.getByStatus(status.name)
            paymentMethod != null -> dao.getByPaymentMethod(paymentMethod.name)
            startMs != null && endMs != null -> dao.getByDateRange(startMs, endMs)
            minAmount != null || maxAmount != null ->
                dao.getByAmountRange(minAmount ?: 0L, maxAmount ?: Long.MAX_VALUE)
            else -> dao.getAll()
        }

        return baseFlow
            .map { entities ->
                // searchQuery (highest precedence) is always the base query when set,
                // so it is not re-applied here. All remaining filters are AND-combined.
                entities.toDomainList().filter { record ->
                    (status == null || record.status == status) &&
                        (paymentMethod == null || record.paymentMethod == paymentMethod) &&
                        (startMs == null || record.timestampMs >= startMs) &&
                        (endMs == null || record.timestampMs <= endMs) &&
                        (minAmount == null || record.amountPaise >= minAmount) &&
                        (maxAmount == null || record.amountPaise <= maxAmount) &&
                        (merchantName == null ||
                            record.merchantName?.contains(merchantName, ignoreCase = true) == true ||
                            record.payeeName?.contains(merchantName, ignoreCase = true) == true)
                }
            }
            .catch { emit(emptyList()) }
            .flowOn(ioDispatcher)
    }
}
