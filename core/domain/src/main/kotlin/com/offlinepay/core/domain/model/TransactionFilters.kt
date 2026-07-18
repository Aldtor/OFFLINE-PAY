package com.offlinepay.core.domain.model

/**
 * Filter criteria for querying transaction history.
 *
 * All filter fields are optional — null means "no filter applied for this field".
 * Multiple filters are combined with AND logic.
 *
 * Design reference: Section 9.3 (TransactionDao query descriptions)
 * Requirements: Req 8.6–8.11 (search and filter acceptance criteria)
 *
 * @param searchQuery Case-insensitive partial match against payee name, merchant name, or UPI ID.
 * @param status Filter to transactions with this specific status. Null = all statuses.
 * @param startDateMs Inclusive start of date range in epoch ms. Null = no lower bound.
 * @param endDateMs Inclusive end of date range in epoch ms. Null = no upper bound.
 * @param minAmountPaise Minimum amount in paise (inclusive). Null = no lower bound.
 * @param maxAmountPaise Maximum amount in paise (inclusive). Null = no upper bound.
 * @param merchantName Case-insensitive partial match against merchant name only.
 * @param paymentMethod Filter to transactions using this specific payment method. Null = all.
 */
data class TransactionFilters(
    val searchQuery: String? = null,
    val status: TransactionStatus? = null,
    val startDateMs: Long? = null,
    val endDateMs: Long? = null,
    val minAmountPaise: Long? = null,
    val maxAmountPaise: Long? = null,
    val merchantName: String? = null,
    val paymentMethod: PaymentMethodType? = null,
) {
    /** True if no filters are applied — returns all transactions. */
    val isEmpty: Boolean get() = searchQuery == null && status == null &&
        startDateMs == null && endDateMs == null &&
        minAmountPaise == null && maxAmountPaise == null &&
        merchantName == null && paymentMethod == null

    companion object {
        /** Convenience factory for an empty (no filter) instance. */
        fun none() = TransactionFilters()
    }
}
