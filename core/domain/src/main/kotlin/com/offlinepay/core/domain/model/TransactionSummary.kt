package com.offlinepay.core.domain.model

/**
 * Aggregated summary of a set of transactions for dashboard display.
 *
 * Used by [TransactionRepository.getTodayTransactionSummary] and
 * [TransactionRepository.getMonthTransactionSummary] to provide counts and
 * totals without returning the full transaction list.
 *
 * All monetary amounts use paise (Long) to avoid floating-point rounding errors.
 *
 * Design reference: Section 9.5 (TransactionRepository interface)
 * Requirements: Req 17.1 (today's payment count), Req 17.2 (this month's payment count)
 *
 * @param count Total number of transactions in the period.
 * @param totalAmountPaise Sum of all transaction amounts in paise.
 */
data class TransactionSummary(
    val count: Int,
    val totalAmountPaise: Long,
) {
    companion object {
        /** Empty summary with zero count and amount. */
        val EMPTY = TransactionSummary(count = 0, totalAmountPaise = 0L)
    }
}
