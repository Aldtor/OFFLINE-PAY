package com.offlinepay.core.domain.usecase.transaction

import com.offlinepay.core.domain.model.TransactionRecord
import com.offlinepay.core.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use case for retrieving this month's transactions for the dashboard summary.
 *
 * Design reference: Section 10.2 (DashboardViewModel — this month's payment count)
 * Requirements: Req 17.2 (this month's payment count and total amount)
 *
 * @param transactionRepository Injected via Hilt.
 */
class GetThisMonthTransactionsUseCase(
    private val transactionRepository: TransactionRepository,
) {
    /** Returns a [Flow] of transactions from the start of the current calendar month. */
    operator fun invoke(): Flow<List<TransactionRecord>> = transactionRepository.getThisMonth()
}
