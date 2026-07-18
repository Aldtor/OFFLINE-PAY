package com.offlinepay.core.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.common.time.DateTimeUtils
import com.offlinepay.core.domain.repository.TransactionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Background WorkManager worker that runs daily to delete transactions older than 90 days (Req 8.12).
 *
 * Configured with battery constraints: Charging + Idle (Req 15.3, design Section 15.3).
 */
@HiltWorker
class TransactionRetentionWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val transactionRepository: TransactionRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val cutoffMs = DateTimeUtils.ninetyDaysAgoMs()
        return try {
            // deleteOlderThan wraps DB errors in AppResult.Failure rather than throwing,
            // so inspect the result explicitly — otherwise a failed purge would report
            // success and never retry.
            when (transactionRepository.deleteOlderThan(cutoffMs)) {
                is AppResult.Success -> Result.success()
                else -> Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
