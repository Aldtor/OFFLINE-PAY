package com.offlinepay.core.domain.usecase

import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.domain.error.DomainError
import com.offlinepay.core.domain.fake.FakeTransactionRepository
import com.offlinepay.core.domain.model.PaymentMethodType
import com.offlinepay.core.domain.model.TransactionFilters
import com.offlinepay.core.domain.model.TransactionRecord
import com.offlinepay.core.domain.model.TransactionStatus
import com.offlinepay.core.domain.usecase.transaction.ExportTransactionsUseCase
import com.offlinepay.core.domain.usecase.transaction.GetTransactionByIdUseCase
import com.offlinepay.core.domain.usecase.transaction.GetTransactionHistoryUseCase
import com.offlinepay.core.domain.usecase.transaction.invoke
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [GetTransactionHistoryUseCase], [GetTransactionByIdUseCase],
 * and [ExportTransactionsUseCase] — Task 5.3.
 */
class TransactionQueryUseCaseTest {

    private lateinit var repository: FakeTransactionRepository
    private lateinit var historyUseCase: GetTransactionHistoryUseCase
    private lateinit var getByIdUseCase: GetTransactionByIdUseCase
    private lateinit var exportUseCase: ExportTransactionsUseCase

    private fun makeTransaction(
        id: String,
        upiId: String = "merchant@upi",
        status: TransactionStatus = TransactionStatus.SUCCESS,
        method: PaymentMethodType = PaymentMethodType.USSD,
        amountPaise: Long = 50_000L,
        timestampMs: Long = 1_000_000L,
    ) = TransactionRecord(
        id = id,
        timestampMs = timestampMs,
        payeeUpiId = upiId,
        amountPaise = amountPaise,
        paymentMethod = method,
        status = status,
        simSlotUsed = 0,
        createdAt = timestampMs,
        updatedAt = timestampMs,
    )

    @BeforeEach
    fun setUp() {
        repository = FakeTransactionRepository()
        historyUseCase = GetTransactionHistoryUseCase { filters ->
            repository.getTransactionsPaged(filters)
        }
        getByIdUseCase = GetTransactionByIdUseCase { id ->
            repository.getTransactionById(id)
        }
        exportUseCase = ExportTransactionsUseCase { filters ->
            repository.getTransactionsForExport(filters)
        }
    }

    // ── GetTransactionHistoryUseCase ──────────────────────────────────────────

    @Test
    fun `history returns empty list when no transactions exist`() = runTest {
        val result = historyUseCase().first()
        result shouldBe emptyList()
    }

    @Test
    fun `history returns all transactions with no filters`() = runTest {
        repository.saveTransaction(makeTransaction("a"))
        repository.saveTransaction(makeTransaction("b"))
        repository.saveTransaction(makeTransaction("c"))

        val result = historyUseCase().first()
        result shouldHaveSize 3
    }

    @Test
    fun `history returns transactions in reverse chronological order`() = runTest {
        repository.saveTransaction(makeTransaction("oldest", timestampMs = 1_000L))
        repository.saveTransaction(makeTransaction("middle", timestampMs = 2_000L))
        repository.saveTransaction(makeTransaction("newest", timestampMs = 3_000L))

        val result = historyUseCase().first()
        result.first().id shouldBe "newest"
        result.last().id shouldBe "oldest"
    }

    @Test
    fun `history filters by status`() = runTest {
        repository.saveTransaction(makeTransaction("success-1", status = TransactionStatus.SUCCESS))
        repository.saveTransaction(makeTransaction("failure-1", status = TransactionStatus.FAILURE))
        repository.saveTransaction(makeTransaction("pending-1", status = TransactionStatus.PENDING))

        val result = historyUseCase(TransactionFilters(status = TransactionStatus.SUCCESS)).first()
        result shouldHaveSize 1
        result.first().id shouldBe "success-1"
    }

    @Test
    fun `history filters by payment method`() = runTest {
        repository.saveTransaction(makeTransaction("ussd-1", method = PaymentMethodType.USSD))
        repository.saveTransaction(makeTransaction("pay123-1", method = PaymentMethodType.PAY123))

        val result = historyUseCase(TransactionFilters(paymentMethod = PaymentMethodType.PAY123)).first()
        result shouldHaveSize 1
        result.first().id shouldBe "pay123-1"
    }

    @Test
    fun `history filters by search query matching payee UPI ID`() = runTest {
        repository.saveTransaction(makeTransaction("a", upiId = "food@upi"))
        repository.saveTransaction(makeTransaction("b", upiId = "shop@upi"))

        val result = historyUseCase(TransactionFilters(searchQuery = "food")).first()
        result shouldHaveSize 1
        result.first().payeeUpiId shouldBe "food@upi"
    }

    @Test
    fun `history no-arg extension invokes with empty filters`() = runTest {
        repository.saveTransaction(makeTransaction("x"))
        repository.saveTransaction(makeTransaction("y"))

        val result = historyUseCase().first()
        result shouldHaveSize 2
    }

    // ── GetTransactionByIdUseCase ─────────────────────────────────────────────

    @Test
    fun `getById returns transaction when it exists`() = runTest {
        repository.saveTransaction(makeTransaction("txn-123"))

        val result = getByIdUseCase("txn-123")
        result.shouldBeInstanceOf<AppResult.Success<TransactionRecord>>()
        (result as AppResult.Success).data.id shouldBe "txn-123"
    }

    @Test
    fun `getById returns Failure when transaction does not exist`() = runTest {
        val result = getByIdUseCase("non-existent")
        result.shouldBeInstanceOf<AppResult.Failure<DomainError.StorageError>>()
    }

    @Test
    fun `getById returns correct transaction when multiple exist`() = runTest {
        repository.saveTransaction(makeTransaction("alpha", upiId = "a@upi"))
        repository.saveTransaction(makeTransaction("beta", upiId = "b@upi"))

        val result = getByIdUseCase("beta")
        result.shouldBeInstanceOf<AppResult.Success<TransactionRecord>>()
        (result as AppResult.Success).data.payeeUpiId shouldBe "b@upi"
    }

    // ── ExportTransactionsUseCase ─────────────────────────────────────────────

    @Test
    fun `export returns all transactions with no filters`() = runTest {
        repository.saveTransaction(makeTransaction("e1"))
        repository.saveTransaction(makeTransaction("e2"))
        repository.saveTransaction(makeTransaction("e3"))

        val result = exportUseCase(TransactionFilters())
        result.shouldBeInstanceOf<AppResult.Success<List<TransactionRecord>>>()
        (result as AppResult.Success).data shouldHaveSize 3
    }

    @Test
    fun `export returns empty list when no transactions match filters`() = runTest {
        repository.saveTransaction(makeTransaction("s1", status = TransactionStatus.SUCCESS))

        val result = exportUseCase(TransactionFilters(status = TransactionStatus.FAILURE))
        result.shouldBeInstanceOf<AppResult.Success<List<TransactionRecord>>>()
        (result as AppResult.Success).data shouldBe emptyList()
    }

    @Test
    fun `export filters by status for PDF generation`() = runTest {
        repository.saveTransaction(makeTransaction("s1", status = TransactionStatus.SUCCESS))
        repository.saveTransaction(makeTransaction("f1", status = TransactionStatus.FAILURE))
        repository.saveTransaction(makeTransaction("s2", status = TransactionStatus.SUCCESS))

        val result = exportUseCase(TransactionFilters(status = TransactionStatus.SUCCESS))
        result.shouldBeInstanceOf<AppResult.Success<List<TransactionRecord>>>()
        (result as AppResult.Success).data shouldHaveSize 2
    }

    @Test
    fun `export returns Failure when repository fails`() = runTest {
        val failingExport = ExportTransactionsUseCase { _ ->
            AppResult.Failure(DomainError.StorageError.DatabaseError("export", "read failure"))
        }

        val result = failingExport(TransactionFilters())
        result.shouldBeInstanceOf<AppResult.Failure<DomainError.StorageError>>()
    }
}
