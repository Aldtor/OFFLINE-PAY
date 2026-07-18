package com.offlinepay.core.analytics

import android.os.Bundle

/**
 * Utility class to scrub PII (Personally Identifiable Information) from analytics parameters.
 * Redacts UPI IDs and Indian phone numbers before any Firebase upload.
 * Completely strips any amount/price parameters by key name.
 *
 * Design reference: Section 14.1 (PII Scrubbing Rules)
 *
 * Note: Account number regex was intentionally removed — it matched valid paise integer values
 * (e.g. "100000000") and caused false positives. Account numbers are never transmitted in
 * analytics events per the privacy-first design principle.
 */
object PiiScrubber {
    private val UPI_ID_REGEX = Regex("[a-zA-Z0-9._\\-]+@[a-zA-Z]+")
    private val PHONE_NUMBER_REGEX = Regex("(?:\\+91|91)?[6-9]\\d{9}")

    /**
     * Scrubs sensitive patterns from a raw string.
     */
    fun scrubString(value: String): String {
        var result = value
        result = UPI_ID_REGEX.replace(result, "[REDACTED_UPI_ID]")
        result = PHONE_NUMBER_REGEX.replace(result, "[REDACTED_PHONE]")
        return result
    }

    /**
     * Scrubs parameters in Map form. Strips amount parameters and scrubs string parameters.
     */
    fun scrubParams(params: Map<String, Any?>): Map<String, Any> {
        val scrubbed = mutableMapOf<String, Any>()
        for ((key, value) in params) {
            if (isAmountKey(key)) continue
            if (value == null) continue

            when (value) {
                is String -> {
                    scrubbed[key] = scrubString(value)
                }
                else -> {
                    scrubbed[key] = value
                }
            }
        }
        return scrubbed
    }

    /**
     * Scrubs parameters in Bundle form. Strips amount parameters and scrubs string parameters.
     */
    fun scrubBundle(bundle: Bundle): Bundle {
        val newBundle = Bundle()
        for (key in bundle.keySet()) {
            if (isAmountKey(key)) continue
            val value = bundle.get(key) ?: continue
            when (value) {
                is String -> {
                    newBundle.putString(key, scrubString(value))
                }
                is Int -> newBundle.putInt(key, value)
                is Long -> newBundle.putLong(key, value)
                is Double -> newBundle.putDouble(key, value)
                is Float -> newBundle.putFloat(key, value)
                is Boolean -> newBundle.putBoolean(key, value)
                else -> newBundle.putString(key, scrubString(value.toString()))
            }
        }
        return newBundle
    }

    private fun isAmountKey(key: String): Boolean {
        val lowerKey = key.lowercase()
        return lowerKey.contains("amount") || lowerKey == "am" || lowerKey.contains("price") || lowerKey.contains("paise")
    }
}
