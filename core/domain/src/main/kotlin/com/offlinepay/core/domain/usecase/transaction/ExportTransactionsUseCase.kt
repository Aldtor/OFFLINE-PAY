package com.offlinepay.core.domain.usecase.transaction

import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.domain.error.DomainError
import com.offlinepay.core.domain.model.TransactionFilters
import com.offlinepay.core.domain.model.TransactionRecord

/**
 * Use case for retrieving a list of transactions for PDF/CSV export.
 *
 * Returns a plain [List] (not a Flow) because export is a one-shot operation.
 * The actual PDF/CSV file generation is handled by the feature layer
 * (`:feature:history`).
 *
 * Design reference: Section 3.3 (Use Cases)
 * Requirements: Req 8.15–8.17 (PDF/CSV export)
 */
fun interface ExportTransactionsUseCase {

    /**
     * Returns all transactions matching [filters] as a one-shot list for PDF/CSV generation.
     *
     * @param filters Filter criteria for selecting which transactions to export.
     *                Use [TransactionFilters.none] to export all transactions.
     * @return [AppResult.Success] with the list of matching [TransactionRecord]s.
     *         [AppResult.Failure] with [DomainError.StorageError.DatabaseError] on DB failure.
     */
    suspend operator fun invoke(
        filters: TransactionFilters,
    ): AppResult<List<TransactionRecord>, DomainError.StorageError>
}
