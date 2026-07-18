package com.offlinepay.core.telephony

import com.offlinepay.core.domain.model.OperatorType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps raw SIM operator name strings from [android.telephony.TelephonyManager]
 * to the app's typed [OperatorType] enum using case-insensitive keyword matching.
 *
 * Design reference: Section 3.9 (`:core:telephony` — OperatorResolver)
 * Requirements: Req 3.2 (operator identification from raw name)
 */
@Singleton
class OperatorResolver @Inject constructor() {

    /**
     * Resolves [rawOperatorName] (e.g. `"AIRTEL"`, `"Vodafone IN"`) to an [OperatorType].
     *
     * @param rawOperatorName Raw string from [android.telephony.TelephonyManager.getSimOperatorName].
     * @return The matched [OperatorType], or [OperatorType.OTHER] if no keyword matches.
     */
    fun resolve(rawOperatorName: String): OperatorType {
        val lower = rawOperatorName.trim().lowercase()
        return when {
            lower.containsAny("airtel", "bharti") -> OperatorType.AIRTEL
            lower.containsAny("vi", "vodafone", "idea", "voda") -> OperatorType.VI
            lower.containsAny("bsnl", "bharat sanchar") -> OperatorType.BSNL
            lower.containsAny("jio", "reliance jio") -> OperatorType.JIO
            else -> OperatorType.OTHER
        }
    }

    private fun String.containsAny(vararg keywords: String): Boolean =
        keywords.any { this.contains(it) }
}
