package com.offlinepay.core.analytics

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.offlinepay.core.common.dispatchers.AppDispatchers
import com.offlinepay.core.connectivity.ConnectivityMonitor
import com.offlinepay.core.connectivity.ConnectivityState
import com.offlinepay.core.data.db.dao.AnalyticsEventDao
import com.offlinepay.core.data.db.entity.AnalyticsEventEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [AnalyticsTracker] that logs events to Firebase Analytics when online,
 * or queues them locally in the database when offline.
 *
 * Design reference: Section 14.2 (Offline Queue), Section 3.7
 * Requirements: Req 13.1 (PII scrubbing), Req 13.2 (Offline queue mechanism),
 *               Req 13.4 (Firebase direct vs local queue)
 */
@Singleton
class FirebaseAnalyticsTracker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val connectivityMonitor: ConnectivityMonitor,
    private val analyticsEventDao: AnalyticsEventDao,
    dispatchers: AppDispatchers,
) : AnalyticsTracker {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private val firebaseAnalytics = FirebaseAnalytics.getInstance(context)

    override fun track(event: AnalyticsEvent) {
        scope.launch {
            val isOnline = connectivityMonitor.connectivityState.value == ConnectivityState.Online
            if (isOnline) {
                logToFirebase(event)
            } else {
                queueEvent(event)
            }
        }
    }

    private fun logToFirebase(event: AnalyticsEvent) {
        val eventName = event.toFirebaseEventName()
        val paramsMap = event.toFirebaseParamsMap()
        val bundle = paramsMap.toBundle()
        val scrubbedBundle = PiiScrubber.scrubBundle(bundle)
        firebaseAnalytics.logEvent(eventName, scrubbedBundle)
    }

    private suspend fun queueEvent(event: AnalyticsEvent) {
        try {
            val eventName = event.toFirebaseEventName()
            val paramsMap = event.toFirebaseParamsMap()
            val eventParamsJson = Json.encodeToString(paramsMap)
            val entity = AnalyticsEventEntity(
                eventName = eventName,
                eventParamsJson = eventParamsJson,
                createdAt = System.currentTimeMillis(),
                isFlushed = false
            )
            analyticsEventDao.insert(entity)
        } catch (e: Exception) {
            // Guard against serialization/DB exceptions to avoid crashing payment flows
        }
    }
}
