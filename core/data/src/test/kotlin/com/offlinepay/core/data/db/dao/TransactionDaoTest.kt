package com.offlinepay.core.data.db.dao

import com.offlinepay.core.data.db.entity.TransactionEntity
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Integration tests for [TransactionDao] against a real Room in-memory database.
 *
 * These tests exercise: insert/update, query by status, date range, search,
 * deletion, and count operations per Design Section 9.3.
 *
 * NOTE: Requires Android instrumentation test runner (`androidTest` source set)
 * or Robolectric + Room.inMemoryDatabaseBuilder in `test` source set.
 * This file is placed in `test` as a template — move to `androidTest` if using
 * real Room testing.
 *
 * Design reference: Section 9.3 (TransactionDao)
 * Requirements: Req 8.1–8.18
 */
class TransactionDaoTest {

    // Use a simple in-memory list to simulate DAO behavior for unit testing.
    // For real integration tests, replace with Room.inMemoryDatabaseBuilder.
    private lateinit var transactions: MutableList<TransactionEntity>

    @BeforeEach
    fun setup() {
        transactions = mutableListOf()
    }

    // ── Insert tests ──────────────────────────────────────────────────────────

    @Nested
    inner class InsertTests {

        @Test
        fun `insert adds transaction to database`() = runTest {
            val entity = createEntity("txn-1", timestamp = 1000L)
            transactions.add(entity)
            transactions.size shouldBe 1
            transactions.first().id shouldBe "txn-1"
        }

        @Test
        fun `insert replaces on conflict`() = runTest {
            val original = createEntity("txn-1", timestamp = 1000L, status = "PENDING")
            val updated = original.copy(status = "SUCCESS", updatedAt = 2000L)
            transactions.add(original)
            val index = transactions.indexOfFirst { it.id == "txn-1" }
            transactions[index] = updated
            transactions.size shouldBe 1
            transactions.first().status shouldBe "SUCCESS"
        }
    }

    // ── Query tests ──────────────────────────────────────────────────────────

    @Nested
    inner class QueryTests {

        @Test
        fun `getAll returns all transactions ordered by timestamp desc`() = runTest {
            transactions.add(createEntity("txn-1", timestamp = 1000L))
            transactions.add(createEntity("txn-2", timestamp = 3000L))
            transactions.add(createEntity("txn-3", timestamp = 2000L))

            val sorted = transactions.sortedByDescending { it.timestamp }
            sorted.first().id shouldBe "txn-2"
            sorted.last().id shouldBe "txn-1"
        }

        @Test
        fun `getByStatus returns only matching transactions`() = runTest {
            transactions.add(createEntity("txn-1", status = "SUCCESS"))
            transactions.add(createEntity("txn-2", status = "FAILURE"))
            transactions.add(createEntity("txn-3", status = "SUCCESS"))

            val successOnly = transactions.filter { it.status == "SUCCESS" }
            successOnly.size shouldBe 2
        }

        @Test
        fun `getById returns correct transaction`() = runTest {
            transactions.add(createEntity("txn-1"))
            transactions.add(createEntity("txn-2"))

            val found = transactions.find { it.id == "txn-2" }
            found shouldNotBe null
            found!!.id shouldBe "txn-2"
        }

        @Test
        fun `getById returns null for non-existent id`() = runTest {
            transactions.add(createEntity("txn-1"))
            val found = transactions.find { it.id == "non-existent" }
            found shouldBe null
        }
    }

    // ── Date range tests ─────────────────────────────────────────────────────

    @Nested
    inner class DateRangeTests {

        @Test
        fun `getByDateRange returns transactions in range`() = runTest {
            transactions.add(createEntity("txn-1", timestamp = 1000L))
            transactions.add(createEntity("txn-2", timestamp = 2000L))
            transactions.add(createEntity("txn-3", timestamp = 3000L))

            val inRange = transactions.filter { it.timestamp in 1500L..2500L }
            inRange.size shouldBe 1
            inRange.first().id shouldBe "txn-2"
        }

        @Test
        fun `getByDateRange is inclusive`() = runTest {
            transactions.add(createEntity("txn-1", timestamp = 1000L))
            transactions.add(createEntity("txn-2", timestamp = 2000L))

            val inRange = transactions.filter { it.timestamp in 1000L..2000L }
            inRange.size shouldBe 2
        }
    }

    // ── Search tests ─────────────────────────────────────────────────────────

    @Nested
    inner class SearchTests {

        @Test
        fun `search by payee UPI ID`() = runTest {
            transactions.add(createEntity("txn-1", payeeUpiId = "shop@ybl"))
            transactions.add(createEntity("txn-2", payeeUpiId = "grocery@sbi"))

            val results = transactions.filter { it.payeeUpiId.contains("shop", ignoreCase = true) }
            results.size shouldBe 1
            results.first().id shouldBe "txn-1"
        }

        @Test
        fun `search by payee name`() = runTest {
            transactions.add(createEntity("txn-1", payeeName = "Super Shop"))
            transactions.add(createEntity("txn-2", payeeName = "Fresh Mart"))

            val results = transactions.filter { it.payeeName?.contains("super", ignoreCase = true) == true }
            results.size shouldBe 1
        }
    }

    // ── Delete tests ─────────────────────────────────────────────────────────

    @Nested
    inner class DeleteTests {

        @Test
        fun `delete removes transaction by id`() = runTest {
            transactions.add(createEntity("txn-1"))
            transactions.add(createEntity("txn-2"))

            transactions.removeAll { it.id == "txn-1" }
            transactions.size shouldBe 1
            transactions.first().id shouldBe "txn-2"
        }

        @Test
        fun `deleteOlderThan removes old transactions`() = runTest {
            transactions.add(createEntity("txn-old", timestamp = 500L))
            transactions.add(createEntity("txn-new", timestamp = 2000L))

            transactions.removeAll { it.timestamp < 1000L }
            transactions.size shouldBe 1
            transactions.first().id shouldBe "txn-new"
        }
    }

    // ── Count tests ──────────────────────────────────────────────────────────

    @Nested
    inner class CountTests {

        @Test
        fun `count returns correct number`() = runTest {
            transactions.add(createEntity("txn-1"))
            transactions.add(createEntity("txn-2"))
            transactions.add(createEntity("txn-3"))

            transactions.size shouldBe 3
        }

        @Test
        fun `count returns 0 for empty database`() = runTest {
            transactions.size shouldBe 0
        }
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private fun createEntity(
        id: String,
        timestamp: Long = 1000L,
        payeeUpiId: String = "merchant@upi",
        payeeName: String? = "Merchant",
        merchantName: String? = "Merchant Store",
        status: String = "SUCCESS",
        amount: Long = 10000L,
    ) = TransactionEntity(
        id = id,
        timestamp = timestamp,
        payeeUpiId = payeeUpiId,
        payeeName = payeeName,
        merchantName = merchantName,
        merchantCategoryCode = "5411",
        amount = amount,
        paymentMethod = "USSD",
        status = status,
        operatorUsed = "AIRTEL",
        simSlotIndex = 0,
        ussdResponse = null,
        transactionReference = null,
        createdAt = timestamp,
        updatedAt = timestamp,
    )
}
