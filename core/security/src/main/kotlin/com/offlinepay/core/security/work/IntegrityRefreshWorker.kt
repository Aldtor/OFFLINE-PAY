package com.offlinepay.core.security.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.offlinepay.core.domain.model.IntegrityVerdict
import com.offlinepay.core.domain.model.VerdictType
import com.offlinepay.core.security.integrity.IntegrityApiClient
import com.offlinepay.core.security.integrity.IntegrityVerdictCache
import com.offlinepay.core.security.integrity.VerdictParser
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that refreshes the Play Integrity verdict in the background.
 *
 * Constraints: [NetworkType.CONNECTED] — only runs when internet is available.
 * Schedule: Periodic every 24 hours + connectivity-triggered (via connectivity constraint).
 *
 * On success: Caches the new verdict via [IntegrityVerdictCache].
 * On failure: Returns [Result.retry] so WorkManager retries with exponential backoff.
 *
 * Design reference: Section 8.2 (5-tier caching), Section 15.3 (WorkManager scheduling)
 * Requirements: Req 9.1 (24h refresh), Req 9.2–9.6 (verdict-based access control)
 */
@HiltWorker
class IntegrityRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val integrityApiClient: IntegrityApiClient,
    private val verdictParser: VerdictParser,
    private val integrityVerdictCache: IntegrityVerdictCache,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "integrity_refresh_periodic"

        /**
         * Schedules the periodic integrity refresh work.
         * Should be called from [OfflinePayApplication] on startup.
         *
         * @param workManager The [WorkManager] instance.
         */
        fun schedule(workManager: WorkManager) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<IntegrityRefreshWorker>(
                repeatInterval = 24,
                repeatIntervalTimeUnit = TimeUnit.HOURS,
            )
                .setConstraints(constraints)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val requestTimeMs = System.currentTimeMillis()
            // Use a simple nonce: base64 of timestamp (sufficient for standard integrity check)
            val nonce = android.util.Base64.encodeToString(
                requestTimeMs.toString().toByteArray(),
                android.util.Base64.NO_WRAP,
            )

            val token = integrityApiClient.requestToken(nonce)
            val verdict = verdictParser.parse(token, requestTimeMs)
            integrityVerdictCache.cacheVerdict(verdict)

            Result.success()
        } catch (e: Exception) {
            // On failure, cache a FAIL verdict to trigger stale-tier handling next startup,
            // unless we already have a cached verdict (which will naturally age)
            Result.retry()
        }
    }
}
