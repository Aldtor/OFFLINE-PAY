package com.offlinepay.core.analytics

/**
 * Interface for tracking structured analytics events.
 *
 * Design reference: Section 3.7 (AnalyticsTracker)
 */
interface AnalyticsTracker {

    /**
     * Tracks the given [event].
     * Decides whether to send it immediately (if online) or queue it locally (if offline).
     */
    fun track(event: AnalyticsEvent)
}
