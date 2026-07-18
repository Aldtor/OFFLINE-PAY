package com.offlinepay.core.domain.usecase.transaction

import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.domain.error.DomainError
import com.offlinepay.core.domain.model.TransactionRecord

/**
 * Use case for retrieving a single transaction record by its UUID.
 *
 * Used by [TransactionReceiptViewModel] to load the receipt screen.
 *
 * Design reference: Section 3.3 (Use Cases)
 * Requirements: Req 8.14 (transaction receipt screen)
 */
fun interface GetTransactionByIdUseCase {

    /**
     * Returns the [TransactionRecord] with the given [id].
     *
     * @param id The UUID of the transaction to retrieve.
     * @return [AppResult.Success] with the record.
     *         [AppResult.Failure] with [DomainError.StorageError.DatabaseError] if not found.
     */
    suspend operator fun invoke(id: String): AppResult<TransactionRecord, DomainError.StorageError>
}
