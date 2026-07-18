package com.offlinepay.core.security

/**
 * Computed security status of the application based on environment checks and Play Integrity.
 *
 * Design reference: Section 8.2 (5-tier caching strategy), Section 8.3
 */
enum class SecurityStatus {
    /** Tier 1: Fresh PASS verdict (<24h age), environment clean. Full payments allowed. */
    PASS,

    /** Tier 2: Stale PASS verdict (24h-72h age), environment clean. Allow with dismissible warning notice. */
    WARN_STALE,

    /** Tier 3: Very stale PASS verdict (>=72h age) or no cached verdict. Allow with persistent warning notice. */
    WARN_VERY_STALE,

    /** Tier 4 / Tamper Block: FAIL verdict or environment tampering (Frida, debugger attached). Payments disabled. */
    BLOCKED
}
