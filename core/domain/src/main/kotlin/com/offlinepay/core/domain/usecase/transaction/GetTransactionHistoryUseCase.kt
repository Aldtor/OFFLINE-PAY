package com.offlinepay.core.domain.usecase.transaction

import com.offlinepay.core.domain.model.TransactionFilters
import com.offlinepay.core.domain.model.TransactionRecord
import kotlinx.coroutines.flow.Flow

/**
 * Use case for retrieving transaction history with optional filtering.
 *
 * Returns a [Flow] that emits whenever the underlying data changes,
 * enabling real-time updates in the history screen.
 *
 * Note: Kotlin `fun interface` members cannot declare default parameter values.
 * Use the [invoke] extension function (no args) to retrieve all transactions without filters.
 *
 * Design reference: Section 3.3 (Use Cases), Section 9.3 (TransactionDao queries)
 * Requirements: Req 8.4 (reverse chronological order), Req 8.6–8.11 (filters)
 */
fun interface GetTransactionHistoryUseCase {

    /**
     * Returns a [Flow] of all transactions matching [filters], in reverse chronological order.
     *
     * Emits an empty list when there are no matching transactions (not an error condition —
     * callers should display an empty state).
     *
     * @param filters Filter criteria. Use [TransactionFilters.none] for all transactions.
     */
    operator fun invoke(filters: TransactionFilters): Flow<List<TransactionRecord>>
}

/**
 * Convenience overload — retrieves all transactions with no filters applied.
 *
 * Kotlin `fun interface` does not support default parameter values in the SAM method,
 * so this extension provides the no-arg call ergonomics instead.
 */
operator fun GetTransactionHistoryUseCase.invoke(): Flow<List<TransactionRecord>> =
    invoke(TransactionFilters())
