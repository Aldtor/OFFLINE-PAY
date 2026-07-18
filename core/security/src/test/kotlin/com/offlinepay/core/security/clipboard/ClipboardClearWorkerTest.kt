package com.offlinepay.core.security.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for [ClipboardClearWorker].
 *
 * Design reference: Section 8.5
 * Requirements: Req 9.25, Req 9.26
 */
class ClipboardClearWorkerTest {

    private val context: Context = mockk(relaxed = true)
    private val workerParams: WorkerParameters = mockk(relaxed = true)
    private val clipboardManager: ClipboardManager = mockk(relaxed = true)

    @BeforeEach
    fun setup() {
        every { context.getSystemService(Context.CLIPBOARD_SERVICE) } returns clipboardManager
    }

    @Nested
    inner class DoWork {

        @Test
        fun `clears clipboard when it contains the sensitive value`() = runTest {
            every { workerParams.inputData } returns workDataOf(
                ClipboardClearWorker.KEY_SENSITIVE_VALUE to "user@upi"
            )
            val clipItem = mockk<ClipData.Item>()
            every { clipItem.text } returns "user@upi"
            val clipData = mockk<ClipData>()
            every { clipData.itemCount } returns 1
            every { clipData.getItemAt(0) } returns clipItem
            every { clipboardManager.primaryClip } returns clipData

            val worker = ClipboardClearWorker(context, workerParams)
            val result = worker.doWork()

            result shouldBe ListenableWorker.Result.success()
            verify(exactly = 1) { clipboardManager.setPrimaryClip(any()) }
        }

        @Test
        fun `does not clear clipboard when content has changed`() = runTest {
            every { workerParams.inputData } returns workDataOf(
                ClipboardClearWorker.KEY_SENSITIVE_VALUE to "user@upi"
            )
            val clipItem = mockk<ClipData.Item>()
            every { clipItem.text } returns "something-else"
            val clipData = mockk<ClipData>()
            every { clipData.itemCount } returns 1
            every { clipData.getItemAt(0) } returns clipItem
            every { clipboardManager.primaryClip } returns clipData

            val worker = ClipboardClearWorker(context, workerParams)
            val result = worker.doWork()

            result shouldBe ListenableWorker.Result.success()
            verify(exactly = 0) { clipboardManager.setPrimaryClip(any()) }
        }

        @Test
        fun `returns success when no sensitive value in input data`() = runTest {
            every { workerParams.inputData } returns workDataOf()

            val worker = ClipboardClearWorker(context, workerParams)
            val result = worker.doWork()

            result shouldBe ListenableWorker.Result.success()
        }
    }
}
