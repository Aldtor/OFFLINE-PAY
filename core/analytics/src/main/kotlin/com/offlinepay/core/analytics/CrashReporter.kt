package com.offlinepay.core.analytics

import com.google.firebase.crashlytics.FirebaseCrashlytics
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wrapper around [FirebaseCrashlytics] that scrubs PII before recording exceptions.
 *
 * All ViewModels should use this instead of calling [FirebaseCrashlytics] directly
 * to ensure UPI IDs, phone numbers, and amounts are never sent to Crashlytics.
 *
 * Design reference: Section 13.4
 * Requirements: Req 12.2 (PII-free crash reports), Req 12.8 (no sensitive custom keys)
 */
@Singleton
class CrashReporter @Inject constructor() {

    private val crashlytics: FirebaseCrashlytics by lazy {
        FirebaseCrashlytics.getInstance()
    }

    /**
     * Records a non-fatal exception to Crashlytics after scrubbing PII from the message.
     *
     * @param throwable The exception to record.
     * @param context Optional context string (scrubbed before sending).
     */
    fun recordException(throwable: Throwable, context: String? = null) {
        context?.let { raw ->
            val scrubbed = PiiScrubber.scrubString(raw)
            crashlytics.setCustomKey("error_context", scrubbed)
        }

        // Scrub the exception message if it contains PII
        val scrubbedException = if (throwable.message != null) {
            val scrubbedMessage = PiiScrubber.scrubString(throwable.message!!)
            RuntimeException(scrubbedMessage, throwable.cause)
        } else {
            throwable
        }

        crashlytics.recordException(scrubbedException)
    }

    /**
     * Sets a scrubbed custom key on the Crashlytics instance.
     */
    fun setCustomKey(key: String, value: String) {
        crashlytics.setCustomKey(key, PiiScrubber.scrubString(value))
    }
}
