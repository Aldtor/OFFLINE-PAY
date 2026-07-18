package com.offlinepay.core.security

import com.offlinepay.core.domain.error.DomainError
import com.offlinepay.core.domain.model.IntegrityVerdict
import com.offlinepay.core.domain.model.VerdictType
import com.offlinepay.core.security.integrity.IntegrityVerdictCache
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SecurityGuard.performStartupChecks].
 *
 * Tests the 5-tier security status cascade:
 * PASS → WARN_STALE → WARN_VERY_STALE → BLOCKED
 *
 * Design reference: Section 8.2, Section 8.3
 * Requirements: Req 9.1–9.15
 */
class SecurityGuardTest {

    // ── Pass scenario ─────────────────────────────────────────────────────────

    @Nested
    inner class PassScenario {

        @Test
        fun `all checks pass with fresh verdict returns PASS`() = runTest {
            val guard = createSecurityGuard(
                certResult = CertificateVerifier.CertCheckResult.MATCH,
                debugStatus = AntiDebugGuard.DebugStatus.OK,
                tamperResult = FridaDetector.TamperCheckResult.CLEAN,
                verdict = IntegrityVerdict(VerdictType.PASS, System.currentTimeMillis()),
                verdictAgeMs = 1000L,
            )

            val result = guard.performStartupChecks()
            result.shouldBeInstanceOf<SecurityGuard.SecurityStatus.Pass>()
        }
    }

    // ── Stale verdict scenarios ───────────────────────────────────────────────

    @Nested
    inner class StaleScenarios {

        @Test
        fun `stale verdict (24-72h old) returns WARN_STALE`() = runTest {
            val guard = createSecurityGuard(
                certResult = CertificateVerifier.CertCheckResult.MATCH,
                debugStatus = AntiDebugGuard.DebugStatus.OK,
                tamperResult = FridaDetector.TamperCheckResult.CLEAN,
                verdict = IntegrityVerdict(VerdictType.PASS, System.currentTimeMillis()),
                verdictAgeMs = IntegrityVerdictCache.STALE_THRESHOLD_MS + 1000L,
            )

            val result = guard.performStartupChecks()
            result.shouldBeInstanceOf<SecurityGuard.SecurityStatus.WarnStale>()
        }

        @Test
        fun `very stale verdict (>72h old) returns WARN_VERY_STALE`() = runTest {
            val guard = createSecurityGuard(
                certResult = CertificateVerifier.CertCheckResult.MATCH,
                debugStatus = AntiDebugGuard.DebugStatus.OK,
                tamperResult = FridaDetector.TamperCheckResult.CLEAN,
                verdict = IntegrityVerdict(VerdictType.PASS, System.currentTimeMillis()),
                verdictAgeMs = IntegrityVerdictCache.VERY_STALE_THRESHOLD_MS + 1000L,
            )

            val result = guard.performStartupChecks()
            result.shouldBeInstanceOf<SecurityGuard.SecurityStatus.WarnVeryStale>()
        }

        @Test
        fun `null verdict returns WARN_VERY_STALE`() = runTest {
            val guard = createSecurityGuard(
                certResult = CertificateVerifier.CertCheckResult.MATCH,
                debugStatus = AntiDebugGuard.DebugStatus.OK,
                tamperResult = FridaDetector.TamperCheckResult.CLEAN,
                verdict = null,
                verdictAgeMs = Long.MAX_VALUE,
            )

            val result = guard.performStartupChecks()
            result.shouldBeInstanceOf<SecurityGuard.SecurityStatus.WarnVeryStale>()
        }
    }

    // ── Blocked scenarios ─────────────────────────────────────────────────────

    @Nested
    inner class BlockedScenarios {

        @Test
        fun `certificate mismatch returns BLOCKED`() = runTest {
            val guard = createSecurityGuard(
                certResult = CertificateVerifier.CertCheckResult.MISMATCH,
                debugStatus = AntiDebugGuard.DebugStatus.OK,
                tamperResult = FridaDetector.TamperCheckResult.CLEAN,
                verdict = IntegrityVerdict(VerdictType.PASS, System.currentTimeMillis()),
                verdictAgeMs = 1000L,
            )

            val result = guard.performStartupChecks()
            result.shouldBeInstanceOf<SecurityGuard.SecurityStatus.Blocked>()
            val blocked = result as SecurityGuard.SecurityStatus.Blocked
            blocked.reason.shouldBeInstanceOf<DomainError.SecurityError.TamperDetected>()
        }

        @Test
        fun `Frida detected returns BLOCKED`() = runTest {
            val guard = createSecurityGuard(
                certResult = CertificateVerifier.CertCheckResult.MATCH,
                debugStatus = AntiDebugGuard.DebugStatus.OK,
                tamperResult = FridaDetector.TamperCheckResult.TAMPERED,
                verdict = IntegrityVerdict(VerdictType.PASS, System.currentTimeMillis()),
                verdictAgeMs = 1000L,
            )

            val result = guard.performStartupChecks()
            result.shouldBeInstanceOf<SecurityGuard.SecurityStatus.Blocked>()
        }

        @Test
        fun `integrity verdict FAIL returns BLOCKED`() = runTest {
            val guard = createSecurityGuard(
                certResult = CertificateVerifier.CertCheckResult.MATCH,
                debugStatus = AntiDebugGuard.DebugStatus.OK,
                tamperResult = FridaDetector.TamperCheckResult.CLEAN,
                verdict = IntegrityVerdict(VerdictType.FAIL, System.currentTimeMillis()),
                verdictAgeMs = 1000L,
            )

            val result = guard.performStartupChecks()
            result.shouldBeInstanceOf<SecurityGuard.SecurityStatus.Blocked>()
        }
    }

    // ── Priority ordering ─────────────────────────────────────────────────────

    @Nested
    inner class PriorityOrdering {

        @Test
        fun `certificate check runs before frida detection`() = runTest {
            val guard = createSecurityGuard(
                certResult = CertificateVerifier.CertCheckResult.MISMATCH,
                debugStatus = AntiDebugGuard.DebugStatus.OK,
                tamperResult = FridaDetector.TamperCheckResult.TAMPERED,
                verdict = IntegrityVerdict(VerdictType.FAIL, System.currentTimeMillis()),
                verdictAgeMs = 1000L,
            )

            val result = guard.performStartupChecks()
            result.shouldBeInstanceOf<SecurityGuard.SecurityStatus.Blocked>()
            val blocked = result as SecurityGuard.SecurityStatus.Blocked
            // Should be cert mismatch, not frida
            blocked.reason.shouldBeInstanceOf<DomainError.SecurityError.TamperDetected>()
            val tamper = blocked.reason as DomainError.SecurityError.TamperDetected
            tamper.source shouldBe DomainError.TamperSource.CERTIFICATE_MISMATCH
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun createSecurityGuard(
        certResult: CertificateVerifier.CertCheckResult,
        debugStatus: AntiDebugGuard.DebugStatus,
        tamperResult: FridaDetector.TamperCheckResult,
        verdict: IntegrityVerdict?,
        verdictAgeMs: Long,
    ): SecurityGuard {
        return SecurityGuard(
            certificateVerifier = FakeCertificateVerifier(certResult),
            antiDebugGuard = FakeAntiDebugGuard(debugStatus),
            fridaDetector = FakeFridaDetector(tamperResult),
            accessibilityAbuseDetector = FakeAccessibilityAbuseDetector(),
            integrityVerdictCache = FakeIntegrityVerdictCache(verdict, verdictAgeMs),
        )
    }
}

// ── Fakes ────────────────────────────────────────────────────────────────────

private class FakeCertificateVerifier(
    private val result: CertificateVerifier.CertCheckResult,
) : CertificateVerifier {
    override fun verify(): CertificateVerifier.CertCheckResult = result
}

private class FakeAntiDebugGuard(
    private val status: AntiDebugGuard.DebugStatus,
) : AntiDebugGuard {
    override fun check(): AntiDebugGuard.DebugStatus = status
    override fun isDebuggableApk(): Boolean = false
}

private class FakeFridaDetector(
    private val result: FridaDetector.TamperCheckResult,
) : FridaDetector {
    override fun check(): FridaDetector.TamperCheckResult = result
}

private class FakeAccessibilityAbuseDetector : AccessibilityAbuseDetector {
    override fun getSuspiciousServices(): List<String> = emptyList()
}

private class FakeIntegrityVerdictCache(
    private val verdict: IntegrityVerdict?,
    private val ageMs: Long,
) : IntegrityVerdictCache {
    override fun getCachedVerdict(): Pair<IntegrityVerdict?, Long> = Pair(verdict, ageMs)
    override suspend fun store(verdict: IntegrityVerdict) {}
    override suspend fun clear() {}
}
