package com.offlinepay.core.domain.usecase.transaction

import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.domain.error.DomainError
import com.offlinepay.core.domain.model.TransactionStatus

/**
 * Use case for updating the status of an existing transaction record.
 *
 * Called after a payment result is known:
 * - USSD success/failure response parsed
 * - 123PAY result confirmed/denied by the user
 *
 * Design reference: Section 3.3 (Use Cases)
 * Requirements: Req 5.9 (USSD success → persist), Req 6.6–6.8 (123PAY result variants)
 */
fun interface UpdateTransactionStatusUseCase {

    /**
     * Updates the [status] of the transaction identified by [id].
     *
     * @param id UUID of the transaction to update.
     * @param status The new [TransactionStatus].
     * @return [AppResult.Success] on success.
     *         [AppResult.Failure] with [DomainError.StorageError.DatabaseError] on failure.
     */
    suspend operator fun invoke(
        id: String,
        status: TransactionStatus,
    ): AppResult<Unit, DomainError.StorageError>
}
