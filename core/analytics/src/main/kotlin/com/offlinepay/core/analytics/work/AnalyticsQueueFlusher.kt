package com.offlinepay.core.analytics.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.analytics.FirebaseAnalytics
import com.offlinepay.core.analytics.PiiScrubber
import com.offlinepay.core.analytics.toBundle
import com.offlinepay.core.data.db.dao.AnalyticsEventDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that flushes queued offline analytics events to Firebase.
 * Matches the design schema of Section 9.1 and Section 14.2.
 *
 * Design reference: Section 12.1, Section 14.2
 * Requirements: Req 13.5 (WorkManager sync flusher)
 */
@HiltWorker
class AnalyticsQueueFlusher @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val analyticsEventDao: AnalyticsEventDao,
) : CoroutineWorker(appContext, workerParams) {

    private val firebaseAnalytics = FirebaseAnalytics.getInstance(appContext)

    override suspend fun doWork(): Result {
        return try {
            // Delete events > 30 days old
            val thirtyDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
            analyticsEventDao.deleteOlderThan(thirtyDaysAgo)

            // Drain the queue in-loop rather than returning Result.retry() per batch.
            // retry() triggers WorkManager exponential backoff between batches, so a
            // large backlog would take a very long time to flush; looping here processes
            // every pending batch in a single run.
            val batchSize = 50
            while (true) {
                val events = analyticsEventDao.getUnflushed(batchSize)
                if (events.isEmpty()) break

                val successIds = mutableListOf<Long>()
                for (eventEntity in events) {
                    try {
                        val eventName = eventEntity.eventName
                        val paramsMap = Json.decodeFromString<Map<String, String>>(eventEntity.eventParamsJson)
                        val bundle = paramsMap.toBundle()

                        // Scrub sensitive information (PII) before upload
                        val scrubbedBundle = PiiScrubber.scrubBundle(bundle)

                        firebaseAnalytics.logEvent(eventName, scrubbedBundle)
                        successIds.add(eventEntity.id)
                    } catch (e: Exception) {
                        // Skip malformed/un-deserializable events to prevent queue blockage
                        successIds.add(eventEntity.id)
                    }
                }

                if (successIds.isNotEmpty()) {
                    analyticsEventDao.markAsFlushed(successIds)
                }

                // Last (partial) batch — nothing more to drain.
                if (events.size < batchSize) break
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
