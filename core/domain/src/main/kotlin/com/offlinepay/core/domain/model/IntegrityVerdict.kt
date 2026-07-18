package com.offlinepay.core.domain.model

import kotlinx.serialization.Serializable

/**
 * The Play Integrity API attestation verdict for this device and app installation.
 *
 * Implements the 5-tier offline-friendly caching strategy from Design Section 8.2.
 * A [VerdictType.FAIL] verdict (where [isGenuineDevice] or [isAppValid] is false) is
 * the only condition that hard-blocks payment features (Req 9.5).
 *
 * The three boolean fields directly reflect the Play Integrity API's three verdict
 * categories: device integrity, app integrity, and environment safety.
 *
 * Design reference: Section 8.2 (Play Integrity — Offline-Friendly Strategy)
 * Requirements: Req 9.1–9.8
 *
 * @param isGenuineDevice True if the device passes basic integrity checks
 *   (not rooted, not emulator, not tampered with).
 * @param isAppValid True if the app binary is unmodified and from a trusted source.
 * @param isEnvironmentSafe True if the runtime environment has no detected threats
 *   (no hooking frameworks, no suspicious accessibility services, etc.).
 * @param verdict Aggregated [VerdictType] classification — [VerdictType.PASS] when all
 *   three boolean flags are true, [VerdictType.FAIL] otherwise. Derived from the
 *   `deviceRecognitionVerdict` array in the Play Integrity token payload.
 * @param cachedAtMs Epoch milliseconds when this verdict was fetched and cached.
 * @param rawVerdict Optional raw verdict details string from the API response
 *   (never stored to DB per Section 8.5 data classification).
 */
@Serializable
data class IntegrityVerdict(
    val isGenuineDevice: Boolean = false,
    val isAppValid: Boolean = false,
    val isEnvironmentSafe: Boolean = false,
    val verdict: VerdictType,
    val cachedAtMs: Long,
    val rawVerdict: String? = null,
) {
    companion object {
        /**
         * Convenience factory for a PASS verdict with all flags set.
         */
        fun pass(cachedAtMs: Long, rawVerdict: String? = null) = IntegrityVerdict(
            isGenuineDevice = true,
            isAppValid = true,
            isEnvironmentSafe = true,
            verdict = VerdictType.PASS,
            cachedAtMs = cachedAtMs,
            rawVerdict = rawVerdict,
        )

        /**
         * Convenience factory for a FAIL verdict with failed flags.
         */
        fun fail(
            isGenuineDevice: Boolean = false,
            isAppValid: Boolean = false,
            isEnvironmentSafe: Boolean = false,
            cachedAtMs: Long,
            rawVerdict: String? = null,
        ) = IntegrityVerdict(
            isGenuineDevice = isGenuineDevice,
            isAppValid = isAppValid,
            isEnvironmentSafe = isEnvironmentSafe,
            verdict = VerdictType.FAIL,
            cachedAtMs = cachedAtMs,
            rawVerdict = rawVerdict,
        )
    }
}

/**
 * Play Integrity verdict classification.
 *
 * Design reference: Section 8.2 (Tier 4 — hard block on FAIL)
 * Requirements: Req 9.1 (PASS verdict cached), Req 9.5 (FAIL hard-blocks payments)
 */
@Serializable
enum class VerdictType {
    /** Device and app pass all integrity checks. Payments fully allowed. */
    PASS,

    /** Device or app failed integrity checks. Payments hard-blocked. */
    FAIL,
}
