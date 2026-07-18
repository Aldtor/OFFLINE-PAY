package com.offlinepay.core.domain.usecase.transaction

import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.domain.error.DomainError
import com.offlinepay.core.domain.model.TransactionRecord

/**
 * Use case for persisting a new transaction record to the local encrypted database.
 *
 * Called immediately before initiating any payment attempt (USSD or 123PAY)
 * with [com.offlinepay.core.domain.model.TransactionStatus.PENDING] so the record
 * survives process death during the payment flow.
 *
 * Design reference: Section 3.3 (Use Cases), Section 9.5 (TransactionRepository)
 * Requirements: Req 8.1 (persist every payment attempt), Req 8.2 (all required fields)
 */
fun interface SaveTransactionUseCase {

    /**
     * Saves [record] to the local database.
     *
     * @return [AppResult.Success] on success.
     *         [AppResult.Failure] with [DomainError.StorageError.DatabaseError] on failure.
     */
    suspend operator fun invoke(
        record: TransactionRecord,
    ): AppResult<Unit, DomainError.StorageError>
}
