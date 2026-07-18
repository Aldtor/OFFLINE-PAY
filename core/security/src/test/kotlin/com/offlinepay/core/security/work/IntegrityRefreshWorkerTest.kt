package com.offlinepay.core.security.work

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.offlinepay.core.domain.model.IntegrityVerdict
import com.offlinepay.core.domain.model.VerdictType
import com.offlinepay.core.security.integrity.IntegrityApiClient
import com.offlinepay.core.security.integrity.IntegrityVerdictCache
import com.offlinepay.core.security.integrity.VerdictParser
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests verifying the execution of [IntegrityRefreshWorker].
 *
 * Design reference: Section 16.2
 * Requirements: Req 9.1 (24h refresh)
 */
class IntegrityRefreshWorkerTest {

    private val context: Context = mockk(relaxed = true)
    private val workerParams: WorkerParameters = mockk(relaxed = true)
    private val integrityApiClient: IntegrityApiClient = mockk()
    private val verdictParser: VerdictParser = mockk()
    private val integrityVerdictCache: IntegrityVerdictCache = mockk(relaxed = true)

    private fun createWorker() = IntegrityRefreshWorker(
        context, workerParams, integrityApiClient, verdictParser, integrityVerdictCache
    )

    @Nested
    inner class DoWork {

        @Test
        fun `successful token request and parse caches verdict and returns SUCCESS`() = runTest {
            val verdict = IntegrityVerdict(VerdictType.PASS, System.currentTimeMillis())
            coEvery { integrityApiClient.requestToken(any()) } returns "fake-token"
            coEvery { verdictParser.parse("fake-token", any()) } returns verdict

            val result = createWorker().doWork()

            result shouldBe ListenableWorker.Result.success()
            coVerify(exactly = 1) { integrityVerdictCache.cacheVerdict(verdict) }
        }

        @Test
        fun `API failure returns RETRY`() = runTest {
            coEvery { integrityApiClient.requestToken(any()) } throws RuntimeException("Network error")

            val result = createWorker().doWork()

            result shouldBe ListenableWorker.Result.retry()
        }
    }
}
