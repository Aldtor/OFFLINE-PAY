package com.offlinepay.core.security

import com.offlinepay.core.domain.model.IntegrityVerdict
import com.offlinepay.core.domain.model.VerdictType
import com.offlinepay.core.domain.repository.IntegrityRepository
import com.offlinepay.core.security.integrity.IntegrityVerdictCache
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Integration test for [SecurityGuard] that wires through [IntegrityVerdictCache]
 * with a mocked [IntegrityRepository] — verifying the full verdict → cache → guard pipeline.
 *
 * Design reference: Section 16.2
 * Requirements: Req 9.1–9.5
 */
class SecurityGuardIntegrationTest {

    private val integrityRepository: IntegrityRepository = mockk()

    private fun createGuard(
        verdictPair: Pair<IntegrityVerdict?, Long>,
    ): SecurityGuard {
        coEvery { integrityRepository.getCachedVerdict() } returns verdictPair

        val cache = IntegrityVerdictCache(integrityRepository)
        return SecurityGuard(
            certificateVerifier = PassCertificateVerifier(),
            antiDebugGuard = PassAntiDebugGuard(),
            fridaDetector = CleanFridaDetector(),
            accessibilityAbuseDetector = NoOpAccessibilityDetector(),
            integrityVerdictCache = cache,
        )
    }

    @Nested
    inner class VerdictCachePipeline {

        @Test
        fun `fresh PASS verdict through real cache returns Pass`() = runTest {
            val verdict = IntegrityVerdict(VerdictType.PASS, System.currentTimeMillis())
            val guard = createGuard(Pair(verdict, 1000L))

            val result = guard.performStartupChecks()
            result.shouldBeInstanceOf<SecurityGuard.SecurityStatus.Pass>()
        }

        @Test
        fun `stale verdict (25h old) through real cache returns WarnStale`() = runTest {
            val verdict = IntegrityVerdict(VerdictType.PASS, System.currentTimeMillis())
            val ageMs = IntegrityVerdictCache.STALE_THRESHOLD_MS + 3_600_000L // 25h
            val guard = createGuard(Pair(verdict, ageMs))

            val result = guard.performStartupChecks()
            result.shouldBeInstanceOf<SecurityGuard.SecurityStatus.WarnStale>()
        }

        @Test
        fun `very stale verdict (73h old) through real cache returns WarnVeryStale`() = runTest {
            val verdict = IntegrityVerdict(VerdictType.PASS, System.currentTimeMillis())
            val ageMs = IntegrityVerdictCache.VERY_STALE_THRESHOLD_MS + 3_600_000L // 73h
            val guard = createGuard(Pair(verdict, ageMs))

            val result = guard.performStartupChecks()
            result.shouldBeInstanceOf<SecurityGuard.SecurityStatus.WarnVeryStale>()
        }

        @Test
        fun `null verdict through real cache returns WarnVeryStale`() = runTest {
            val guard = createGuard(Pair(null, Long.MAX_VALUE))

            val result = guard.performStartupChecks()
            result.shouldBeInstanceOf<SecurityGuard.SecurityStatus.WarnVeryStale>()
        }

        @Test
        fun `FAIL verdict through real cache returns Blocked`() = runTest {
            val verdict = IntegrityVerdict(VerdictType.FAIL, System.currentTimeMillis())
            val guard = createGuard(Pair(verdict, 1000L))

            val result = guard.performStartupChecks()
            result.shouldBeInstanceOf<SecurityGuard.SecurityStatus.Blocked>()
        }
    }

    // ── Stub implementations that always pass ────────────────────────────────

    private class PassCertificateVerifier : CertificateVerifier {
        override fun verify() = CertificateVerifier.CertCheckResult.MATCH
    }

    private class PassAntiDebugGuard : AntiDebugGuard {
        override fun check() = AntiDebugGuard.DebugStatus.OK
        override fun isDebuggableApk() = false
    }

    private class CleanFridaDetector : FridaDetector {
        override fun check() = FridaDetector.TamperCheckResult.CLEAN
    }

    private class NoOpAccessibilityDetector : AccessibilityAbuseDetector {
        override fun getSuspiciousServices(): List<String> = emptyList()
    }
}
