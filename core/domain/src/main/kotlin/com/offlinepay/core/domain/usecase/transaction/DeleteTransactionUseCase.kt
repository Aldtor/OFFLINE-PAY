package com.offlinepay.core.domain.usecase.transaction

import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.domain.error.DomainError
import com.offlinepay.core.domain.repository.TransactionRepository

/**
 * Use case for permanently deleting a transaction record after user confirmation.
 *
 * Design reference: Section 3.3 (Use Cases)
 * Requirements: Req 8.13 (delete with user confirmation)
 *
 * @param transactionRepository Injected via Hilt.
 */
class DeleteTransactionUseCase(
    private val transactionRepository: TransactionRepository,
) {
    /**
     * Permanently removes the transaction with [id] from the local database.
     *
     * @return [AppResult.Success] on success.
     *         [AppResult.Failure] with [DomainError.StorageError.DatabaseError] on failure.
     */
    suspend operator fun invoke(id: String): AppResult<Unit, DomainError.StorageError> =
        transactionRepository.delete(id)
}
