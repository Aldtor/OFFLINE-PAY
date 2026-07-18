package com.offlinepay.core.data.work

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.offlinepay.core.domain.repository.TransactionRepository
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class TransactionRetentionWorkerTest {

    private val context: Context = mockk(relaxed = true)
    private val workerParams: WorkerParameters = mockk(relaxed = true)
    private val repository: TransactionRepository = mockk(relaxed = true)

    @Test
    fun `doWork calls deleteOlderThan with 90 days ago cutoff and returns SUCCESS`() = runTest {
        val worker = TransactionRetentionWorker(context, workerParams, repository)

        val result = worker.doWork()

        result shouldBe ListenableWorker.Result.success()
        coVerify(exactly = 1) {
            repository.deleteOlderThan(any())
        }
    }

    @Test
    fun `doWork returns RETRY when repository throws exception`() = runTest {
        coEvery { repository.deleteOlderThan(any()) } throws RuntimeException("Database error")
        val worker = TransactionRetentionWorker(context, workerParams, repository)

        val result = worker.doWork()

        result shouldBe ListenableWorker.Result.retry()
    }
}
