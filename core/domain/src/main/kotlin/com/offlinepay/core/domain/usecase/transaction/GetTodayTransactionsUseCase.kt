package com.offlinepay.core.domain.usecase.transaction

import com.offlinepay.core.domain.model.TransactionRecord
import com.offlinepay.core.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use case for retrieving today's transactions for the dashboard summary.
 *
 * Design reference: Section 10.2 (DashboardViewModel — today's payment count)
 * Requirements: Req 17.1 (today's payment count and total amount)
 *
 * @param transactionRepository Injected via Hilt.
 */
class GetTodayTransactionsUseCase(
    private val transactionRepository: TransactionRepository,
) {
    /** Returns a [Flow] of transactions initiated today (since 00:00 local time). */
    operator fun invoke(): Flow<List<TransactionRecord>> = transactionRepository.getToday()
}
