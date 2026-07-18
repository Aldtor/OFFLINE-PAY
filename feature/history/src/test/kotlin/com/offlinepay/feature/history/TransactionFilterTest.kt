package com.offlinepay.feature.history

import com.offlinepay.core.domain.model.OperatorType
import com.offlinepay.core.domain.model.PaymentMethodType
import com.offlinepay.core.domain.model.TransactionFilters
import com.offlinepay.core.domain.model.TransactionRecord
import com.offlinepay.core.domain.model.TransactionStatus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for [TransactionFilters] and filter logic.
 *
 * Verifies: search query matching, date range filtering, status filtering,
 * payment method filtering, and combined filter scenarios.
 *
 * Design reference: Section 9.5 (History filters)
 * Requirements: Req 8.14 (search), Req 8.15 (filter by status/method)
 */
class TransactionFilterTest {

    private val sampleTransactions = listOf(
        createTransaction(
            id = "txn-1",
            payeeUpiId = "shop@ybl",
            payeeName = "Super Shop",
            merchantName = "Super Shop Pvt Ltd",
            status = TransactionStatus.SUCCESS,
            amountPaise = 50000L,
            timestampMs = 1700000000000L,
            paymentMethod = PaymentMethodType.USSD,
        ),
        createTransaction(
            id = "txn-2",
            payeeUpiId = "grocery@sbi",
            payeeName = "Fresh Groceries",
            merchantName = "Fresh Mart",
            status = TransactionStatus.FAILURE,
            amountPaise = 15000L,
            timestampMs = 1700100000000L,
            paymentMethod = PaymentMethodType.PAY123,
        ),
        createTransaction(
            id = "txn-3",
            payeeUpiId = "restaurant@paytm",
            payeeName = "Taj Restaurant",
            merchantName = "Taj Foods",
            status = TransactionStatus.PENDING,
            amountPaise = 200000L,
            timestampMs = 1700200000000L,
            paymentMethod = PaymentMethodType.USSD,
        ),
    )

    // ── Search query tests ─────────────────────────────────────────────────────

    @Nested
    inner class SearchQuery {

        @Test
        fun `empty query matches all transactions`() {
            val filters = TransactionFilters(searchQuery = "")
            val filtered = applyFilters(sampleTransactions, filters)
            filtered.size shouldBe 3
        }

        @Test
        fun `search by payee name`() {
            val filters = TransactionFilters(searchQuery = "Super Shop")
            val filtered = applyFilters(sampleTransactions, filters)
            filtered.size shouldBe 1
            filtered.first().id shouldBe "txn-1"
        }

        @Test
        fun `search by UPI ID`() {
            val filters = TransactionFilters(searchQuery = "grocery@sbi")
            val filtered = applyFilters(sampleTransactions, filters)
            filtered.size shouldBe 1
            filtered.first().id shouldBe "txn-2"
        }

        @Test
        fun `search is case-insensitive`() {
            val filters = TransactionFilters(searchQuery = "TAJ RESTAURANT")
            val filtered = applyFilters(sampleTransactions, filters)
            filtered.size shouldBe 1
            filtered.first().id shouldBe "txn-3"
        }

        @Test
        fun `search with no matches returns empty`() {
            val filters = TransactionFilters(searchQuery = "nonexistent")
            val filtered = applyFilters(sampleTransactions, filters)
            filtered.size shouldBe 0
        }
    }

    // ── Status filter tests ─────────────────────────────────────────────────────

    @Nested
    inner class StatusFilter {

        @Test
        fun `filter by SUCCESS status`() {
            val filters = TransactionFilters(statusFilter = TransactionStatus.SUCCESS)
            val filtered = applyFilters(sampleTransactions, filters)
            filtered.size shouldBe 1
            filtered.all { it.status == TransactionStatus.SUCCESS } shouldBe true
        }

        @Test
        fun `filter by FAILURE status`() {
            val filters = TransactionFilters(statusFilter = TransactionStatus.FAILURE)
            val filtered = applyFilters(sampleTransactions, filters)
            filtered.size shouldBe 1
            filtered.first().id shouldBe "txn-2"
        }

        @Test
        fun `null status filter matches all`() {
            val filters = TransactionFilters(statusFilter = null)
            val filtered = applyFilters(sampleTransactions, filters)
            filtered.size shouldBe 3
        }
    }

    // ── Payment method filter tests ─────────────────────────────────────────────

    @Nested
    inner class MethodFilter {

        @Test
        fun `filter by USSD method`() {
            val filters = TransactionFilters(methodFilter = PaymentMethodType.USSD)
            val filtered = applyFilters(sampleTransactions, filters)
            filtered.size shouldBe 2
            filtered.all { it.paymentMethod == PaymentMethodType.USSD } shouldBe true
        }

        @Test
        fun `filter by PAY123 method`() {
            val filters = TransactionFilters(methodFilter = PaymentMethodType.PAY123)
            val filtered = applyFilters(sampleTransactions, filters)
            filtered.size shouldBe 1
            filtered.first().paymentMethod shouldBe PaymentMethodType.PAY123
        }
    }

    // ── Date range filter tests ──────────────────────────────────────────────────

    @Nested
    inner class DateRangeFilter {

        @Test
        fun `filter by start date`() {
            val filters = TransactionFilters(startDateMs = 1700100000000L)
            val filtered = applyFilters(sampleTransactions, filters)
            filtered.size shouldBe 2
        }

        @Test
        fun `filter by end date`() {
            val filters = TransactionFilters(endDateMs = 1700100000000L)
            val filtered = applyFilters(sampleTransactions, filters)
            filtered.size shouldBe 2
        }

        @Test
        fun `filter by date range`() {
            val filters = TransactionFilters(
                startDateMs = 1700050000000L,
                endDateMs = 1700150000000L,
            )
            val filtered = applyFilters(sampleTransactions, filters)
            filtered.size shouldBe 1
            filtered.first().id shouldBe "txn-2"
        }
    }

    // ── Combined filter tests ────────────────────────────────────────────────────

    @Nested
    inner class CombinedFilters {

        @Test
        fun `combine status and method filters`() {
            val filters = TransactionFilters(
                statusFilter = TransactionStatus.SUCCESS,
                methodFilter = PaymentMethodType.USSD,
            )
            val filtered = applyFilters(sampleTransactions, filters)
            filtered.size shouldBe 1
            filtered.first().id shouldBe "txn-1"
        }

        @Test
        fun `combine search and status filters`() {
            val filters = TransactionFilters(
                searchQuery = "shop",
                statusFilter = TransactionStatus.SUCCESS,
            )
            val filtered = applyFilters(sampleTransactions, filters)
            filtered.size shouldBe 1
            filtered.first().id shouldBe "txn-1"
        }
    }

    // ── Helper functions ────────────────────────────────────────────────────────

    private fun applyFilters(
        transactions: List<TransactionRecord>,
        filters: TransactionFilters,
    ): List<TransactionRecord> {
        return transactions.filter { txn ->
            val matchesSearch = filters.searchQuery.isNullOrBlank() ||
                txn.payeeName.orEmpty().contains(filters.searchQuery!!, ignoreCase = true) ||
                txn.payeeUpiId.contains(filters.searchQuery!!, ignoreCase = true) ||
                txn.merchantName.orEmpty().contains(filters.searchQuery!!, ignoreCase = true)

            val matchesStatus = filters.statusFilter == null ||
                txn.status == filters.statusFilter

            val matchesMethod = filters.methodFilter == null ||
                txn.paymentMethod == filters.methodFilter

            val matchesStartDate = filters.startDateMs == null ||
                txn.timestampMs >= filters.startDateMs!!

            val matchesEndDate = filters.endDateMs == null ||
                txn.timestampMs <= filters.endDateMs!!

            matchesSearch && matchesStatus && matchesMethod && matchesStartDate && matchesEndDate
        }
    }

    private fun createTransaction(
        id: String,
        payeeUpiId: String,
        payeeName: String?,
        merchantName: String?,
        status: TransactionStatus,
        amountPaise: Long,
        timestampMs: Long,
        paymentMethod: PaymentMethodType,
    ) = TransactionRecord(
        id = id,
        timestampMs = timestampMs,
        payeeUpiId = payeeUpiId,
        payeeName = payeeName,
        merchantName = merchantName,
        amountPaise = amountPaise,
        paymentMethod = paymentMethod,
        status = status,
        operatorUsed = OperatorType.AIRTEL,
        simSlotUsed = 0,
        createdAt = timestampMs,
        updatedAt = timestampMs,
    )
}
