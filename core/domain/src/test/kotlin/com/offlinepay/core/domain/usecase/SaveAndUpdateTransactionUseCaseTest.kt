package com.offlinepay.core.domain.usecase

import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.domain.error.DomainError
import com.offlinepay.core.domain.fake.FakeTransactionRepository
import com.offlinepay.core.domain.model.PaymentMethodType
import com.offlinepay.core.domain.model.TransactionRecord
import com.offlinepay.core.domain.model.TransactionStatus
import com.offlinepay.core.domain.usecase.transaction.SaveTransactionUseCase
import com.offlinepay.core.domain.usecase.transaction.UpdateTransactionStatusUseCase
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SaveTransactionUseCase] and [UpdateTransactionStatusUseCase].
 */
class SaveAndUpdateTransactionUseCaseTest {

    private lateinit var repository: FakeTransactionRepository
    private lateinit var saveUseCase: SaveTransactionUseCase
    private lateinit var updateUseCase: UpdateTransactionStatusUseCase

    private fun makeTransaction(id: String = "txn-001") = TransactionRecord(
        id = id,
        timestampMs = 1_000_000L,
        payeeUpiId = "merchant@upi",
        amountPaise = 50000L,
        paymentMethod = PaymentMethodType.USSD,
        status = TransactionStatus.PENDING,
        simSlotUsed = 0,
        createdAt = 1_000_000L,
        updatedAt = 1_000_000L,
    )

    @BeforeEach
    fun setUp() {
        repository = FakeTransactionRepository()
        saveUseCase = SaveTransactionUseCase { record -> repository.save(record) }
        updateUseCase = UpdateTransactionStatusUseCase { id, status ->
            repository.updateTransactionStatus(id, status)
        }
    }

    @Test
    fun `save persists transaction with PENDING status`() = runTest {
        val txn = makeTransaction()
        val result = saveUseCase(txn)
        result.shouldBeInstanceOf<AppResult.Success<Unit>>()
        repository.transactions.first().status shouldBe TransactionStatus.PENDING
    }

    @Test
    fun `save returns Failure when repository fails`() = runTest {
        repository.saveError = DomainError.StorageError.DatabaseError("save", "disk full")
        val result = saveUseCase(makeTransaction())
        result.shouldBeInstanceOf<AppResult.Failure<DomainError.StorageError>>()
    }

    @Test
    fun `updateStatus changes transaction status to SUCCESS`() = runTest {
        saveUseCase(makeTransaction("txn-001"))
        val result = updateUseCase("txn-001", TransactionStatus.SUCCESS)
        result.shouldBeInstanceOf<AppResult.Success<Unit>>()
        repository.transactions.first { it.id == "txn-001" }.status shouldBe TransactionStatus.SUCCESS
    }

    @Test
    fun `updateStatus changes transaction status to FAILURE`() = runTest {
        saveUseCase(makeTransaction("txn-002"))
        updateUseCase("txn-002", TransactionStatus.FAILURE)
        repository.transactions.first { it.id == "txn-002" }.status shouldBe TransactionStatus.FAILURE
    }

    @Test
    fun `updateStatus to UNKNOWN persists unknown status`() = runTest {
        saveUseCase(makeTransaction("txn-003"))
        updateUseCase("txn-003", TransactionStatus.UNKNOWN)
        repository.transactions.first { it.id == "txn-003" }.status shouldBe TransactionStatus.UNKNOWN
    }

    @Test
    fun `save multiple transactions accumulates correctly`() = runTest {
        saveUseCase(makeTransaction("a"))
        saveUseCase(makeTransaction("b"))
        saveUseCase(makeTransaction("c"))
        repository.transactions.size shouldBe 3
    }

    @Test
    fun `getById returns saved transaction`() = runTest {
        saveUseCase(makeTransaction("find-me"))
        val result = repository.getById("find-me")
        result.shouldBeInstanceOf<AppResult.Success<TransactionRecord>>()
        (result as AppResult.Success).data.id shouldBe "find-me"
    }

    @Test
    fun `getById returns Failure for non-existent id`() = runTest {
        val result = repository.getById("does-not-exist")
        result.shouldBeInstanceOf<AppResult.Failure<DomainError.StorageError>>()
    }
}
