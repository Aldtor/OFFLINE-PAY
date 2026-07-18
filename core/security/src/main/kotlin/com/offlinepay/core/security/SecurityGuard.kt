package com.offlinepay.core.security

import com.offlinepay.core.domain.error.DomainError
import com.offlinepay.core.domain.model.VerdictType
import com.offlinepay.core.security.integrity.IntegrityVerdictCache
import com.offlinepay.core.security.integrity.IntegrityVerdictCache.Companion.STALE_THRESHOLD_MS
import com.offlinepay.core.security.integrity.IntegrityVerdictCache.Companion.VERY_STALE_THRESHOLD_MS
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Primary security orchestrator that evaluates all runtime security checks
 * and returns a tiered [SecurityStatus].
 *
 * ### Check sequence (Design Section 8.2/8.3):
 * 1. [CertificateVerifier] — Signing cert mismatch → immediate BLOCKED
 * 2. [AntiDebugGuard] — Debugger detected → immediate BLOCKED (release only)
 * 3. [FridaDetector] — Frida/hooking detected → immediate BLOCKED
 * 4. [IntegrityVerdictCache] — Stale verdict → WARN_STALE or WARN_VERY_STALE
 *
 * Accessibility abuse detection is informational and does not affect [SecurityStatus].
 *
 * Design reference: Section 8.2, Section 8.3, Section 12.3
 * Requirements: Req 9.1–9.15
 */
@Singleton
class SecurityGuard @Inject constructor(
    private val certificateVerifier: CertificateVerifier,
    private val antiDebugGuard: AntiDebugGuard,
    private val fridaDetector: FridaDetector,
    private val accessibilityAbuseDetector: AccessibilityAbuseDetector,
    private val integrityVerdictCache: IntegrityVerdictCache,
) {

    /**
     * The composite security status computed by [performStartupChecks].
     *
     * Corresponds to the 5-tier Play Integrity strategy in Design Section 8.2.
     */
    sealed class SecurityStatus {

        /**
         * All checks passed. Payments are fully allowed.
         * Integrity verdict is fresh (< 24h).
         */
        data object Pass : SecurityStatus()

        /**
         * Integrity verdict is stale (24–72h old).
         * Payments are allowed with a dismissible amber notice.
         *
         * @param ageMs Age of the cached verdict in milliseconds.
         */
        data class WarnStale(val ageMs: Long) : SecurityStatus()

        /**
         * Integrity verdict is very stale (> 72h old or absent).
         * Payments are allowed with a persistent red banner.
         *
         * @param ageMs Age of the cached verdict, or Long.MAX_VALUE if absent.
         */
        data class WarnVeryStale(val ageMs: Long) : SecurityStatus()

        /**
         * A hard security violation was detected. All payment features are blocked.
         *
         * @param reason The domain error describing why payments are blocked.
         * @param suspiciousAccessibilityServices List of suspicious accessibility service names (informational).
         */
        data class Blocked(
            val reason: DomainError,
            val suspiciousAccessibilityServices: List<String> = emptyList(),
        ) : SecurityStatus()
    }

    /**
     * Performs all startup security checks and returns the composite [SecurityStatus].
     *
     * This is called during the splash screen (Design Section 12.3).
     * Order matters: tamper detection is run before Play Integrity checks.
     *
     * @return The [SecurityStatus] that determines what the app displays on startup.
     */
    suspend fun performStartupChecks(): SecurityStatus {
        // Step 1: Certificate verification
        val certResult = certificateVerifier.verify()
        if (certResult == CertificateVerifier.CertCheckResult.MISMATCH) {
            return SecurityStatus.Blocked(
                reason = DomainError.SecurityError.TamperDetected(
                    source = DomainError.TamperSource.CERTIFICATE_MISMATCH,
                ),
            )
        }

        // Step 2: Anti-debug check (release builds only)
        val debugStatus = antiDebugGuard.check()
        if (debugStatus == AntiDebugGuard.DebugStatus.DEBUGGER_ATTACHED &&
            !antiDebugGuard.isDebuggableApk()
        ) {
            // Only block in release builds — let debug builds pass
            return SecurityStatus.Blocked(
                reason = DomainError.SecurityError.DebuggerDetected,
            )
        }

        // Step 3: Frida detection (dispatches to IO internally)
        val tamperResult = fridaDetector.check()
        if (tamperResult == FridaDetector.TamperCheckResult.TAMPERED) {
            return SecurityStatus.Blocked(
                reason = DomainError.SecurityError.TamperDetected(
                    source = DomainError.TamperSource.FRIDA_DETECTED,
                ),
            )
        }

        // Step 4: Play Integrity verdict check
        val (verdict, ageMs) = integrityVerdictCache.getCachedVerdict()

        if (verdict?.verdict == VerdictType.FAIL) {
            return SecurityStatus.Blocked(
                reason = DomainError.SecurityError.IntegrityFailed(
                    verdictDetails = "Play Integrity verdict: FAIL",
                ),
            )
        }

        // Collect accessibility service info (informational, non-blocking)
        val suspiciousServices = accessibilityAbuseDetector.getSuspiciousServices()

        // Determine staleness tier
        return when {
            verdict == null || ageMs >= VERY_STALE_THRESHOLD_MS -> SecurityStatus.WarnVeryStale(ageMs)
            ageMs >= STALE_THRESHOLD_MS -> SecurityStatus.WarnStale(ageMs)
            else -> SecurityStatus.Pass
        }
    }
}
