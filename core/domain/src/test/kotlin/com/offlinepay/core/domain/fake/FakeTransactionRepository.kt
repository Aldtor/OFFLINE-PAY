package com.offlinepay.core.domain.fake

import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.domain.error.DomainError
import com.offlinepay.core.domain.model.TransactionFilters
import com.offlinepay.core.domain.model.TransactionRecord
import com.offlinepay.core.domain.model.TransactionStatus
import com.offlinepay.core.domain.model.TransactionSummary
import com.offlinepay.core.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory fake [TransactionRepository] for use in unit tests.
 *
 * Stores transactions in a [MutableStateFlow]-backed list so that
 * flows emit updates when the list changes.
 */
class FakeTransactionRepository : TransactionRepository {

    private val _transactions = MutableStateFlow<List<TransactionRecord>>(emptyList())
    val transactions: List<TransactionRecord> get() = _transactions.value

    var saveError: DomainError.StorageError? = null
    var updateError: DomainError.StorageError? = null

    // ── Primary interface methods ─────────────────────────────────────────────

    override suspend fun saveTransaction(
        record: TransactionRecord,
    ): AppResult<Unit, DomainError.StorageError> {
        saveError?.let { return AppResult.Failure(it) }
        _transactions.value = _transactions.value + record
        return AppResult.Success(Unit)
    }

    override suspend fun updateTransactionStatus(
        id: String,
        status: TransactionStatus,
    ): AppResult<Unit, DomainError.StorageError> {
        updateError?.let { return AppResult.Failure(it) }
        _transactions.value = _transactions.value.map { txn ->
            if (txn.id == id) txn.copy(status = status, updatedAt = System.currentTimeMillis()) else txn
        }
        return AppResult.Success(Unit)
    }

    override suspend fun getTransactionById(
        id: String,
    ): AppResult<TransactionRecord, DomainError.StorageError> {
        val txn = _transactions.value.find { it.id == id }
        return if (txn != null) AppResult.Success(txn)
        else AppResult.Failure(DomainError.StorageError.DatabaseError("getTransactionById", "Not found: $id"))
    }

    override fun getTransactionsPaged(filters: TransactionFilters): Flow<List<TransactionRecord>> =
        _transactions.map { list -> applyFilters(list, filters) }

    override suspend fun getTransactionsForExport(
        filters: TransactionFilters,
    ): AppResult<List<TransactionRecord>, DomainError.StorageError> =
        AppResult.Success(applyFilters(_transactions.value, filters))

    override suspend fun deleteTransaction(id: String): AppResult<Unit, DomainError.StorageError> {
        _transactions.value = _transactions.value.filter { it.id != id }
        return AppResult.Success(Unit)
    }

    override suspend fun getTodayTransactionSummary(): AppResult<TransactionSummary, DomainError.StorageError> {
        val todayList = _transactions.value
        val summary = TransactionSummary(
            count = todayList.size,
            totalAmountPaise = todayList.sumOf { it.amountPaise },
        )
        return AppResult.Success(summary)
    }

    override suspend fun getMonthTransactionSummary(): AppResult<TransactionSummary, DomainError.StorageError> {
        val monthList = _transactions.value
        val summary = TransactionSummary(
            count = monthList.size,
            totalAmountPaise = monthList.sumOf { it.amountPaise },
        )
        return AppResult.Success(summary)
    }

    override suspend fun deleteTransactionsOlderThan(
        timestampMs: Long,
    ): AppResult<Int, DomainError.StorageError> {
        val before = _transactions.value.size
        _transactions.value = _transactions.value.filter { it.timestampMs >= timestampMs }
        val deletedCount = before - _transactions.value.size
        return AppResult.Success(deletedCount)
    }

    // ── Legacy methods still required by the interface ────────────────────────

    override fun getToday(): Flow<List<TransactionRecord>> = _transactions

    override fun getThisMonth(): Flow<List<TransactionRecord>> = _transactions

    override suspend fun getForExport(
        startMs: Long,
        endMs: Long,
    ): AppResult<List<TransactionRecord>, DomainError.StorageError> {
        val result = _transactions.value.filter { it.timestampMs in startMs..endMs }
        return AppResult.Success(result)
    }

    override suspend fun deleteOlderThan(
        cutoffMs: Long,
    ): AppResult<Unit, DomainError.StorageError> {
        _transactions.value = _transactions.value.filter { it.timestampMs >= cutoffMs }
        return AppResult.Success(Unit)
    }

    // ── Legacy save / updateStatus aliases ───────────────────────────────────

    override suspend fun save(
        transaction: TransactionRecord,
    ): AppResult<Unit, DomainError.StorageError> = saveTransaction(transaction)

    override suspend fun updateStatus(
        id: String,
        status: TransactionStatus,
        updatedAt: Long,
    ): AppResult<Unit, DomainError.StorageError> = updateTransactionStatus(id, status)

    override suspend fun getById(
        id: String,
    ): AppResult<TransactionRecord, DomainError.StorageError> = getTransactionById(id)

    override fun getAll(filters: TransactionFilters): Flow<List<TransactionRecord>> =
        getTransactionsPaged(filters)

    override suspend fun delete(id: String): AppResult<Unit, DomainError.StorageError> =
        deleteTransaction(id)

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun applyFilters(
        list: List<TransactionRecord>,
        filters: TransactionFilters,
    ): List<TransactionRecord> {
        val query = filters.searchQuery
        return list.filter { txn ->
            (filters.status == null || txn.status == filters.status) &&
                (filters.paymentMethod == null || txn.paymentMethod == filters.paymentMethod) &&
                (query == null ||
                    txn.payeeUpiId.contains(query, ignoreCase = true) ||
                    txn.payeeName?.contains(query, ignoreCase = true) == true ||
                    txn.merchantName?.contains(query, ignoreCase = true) == true)
        }.sortedByDescending { it.timestampMs }
    }
}
