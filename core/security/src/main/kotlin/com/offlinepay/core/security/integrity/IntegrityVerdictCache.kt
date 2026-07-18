package com.offlinepay.core.security.integrity

import com.offlinepay.core.domain.model.IntegrityVerdict
import com.offlinepay.core.domain.repository.IntegrityRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cache layer for the Play Integrity verdict, backed by [IntegrityRepository].
 *
 * Provides a simple interface to read and write the cached verdict with its age.
 * The actual storage is in EncryptedSharedPreferences via [IntegrityRepository].
 *
 * Implements the 5-tier offline-friendly caching strategy from Design Section 8.2.
 * Age thresholds:
 * - < 24h → Tier 1: PASS (fresh)
 * - 24–72h → Tier 2: STALE (dismissible notice)
 * - > 72h or null → Tier 3: VERY_STALE (persistent banner)
 * - FAIL verdict → Tier 4: BLOCKED
 *
 * Design reference: Section 8.2 (5-tier caching strategy)
 * Requirements: Req 9.1, Req 9.2, Req 9.3, Req 9.4, Req 9.6
 */
@Singleton
class IntegrityVerdictCache @Inject constructor(
    private val integrityRepository: IntegrityRepository,
) {

    /**
     * Returns the cached [IntegrityVerdict] and its age in milliseconds.
     *
     * @return Pair(verdict, ageMs) where:
     *   - verdict is null if no verdict has ever been cached
     *   - ageMs = currentTimeMs - cachedAtMs (Long.MAX_VALUE if no verdict)
     */
    suspend fun getCachedVerdict(): Pair<IntegrityVerdict?, Long> {
        return integrityRepository.getCachedVerdict()
    }

    /**
     * Caches a fresh [IntegrityVerdict].
     *
     * @param verdict The verdict to cache with its [IntegrityVerdict.cachedAtMs] timestamp.
     */
    suspend fun cacheVerdict(verdict: IntegrityVerdict) {
        integrityRepository.cacheVerdict(verdict)
    }

    /**
     * Clears the cached verdict.
     */
    suspend fun clearVerdict() {
        integrityRepository.clearVerdict()
    }

    companion object {
        /** 24 hours in milliseconds — boundary between Tier 1 and Tier 2. */
        const val STALE_THRESHOLD_MS = 24L * 60 * 60 * 1000

        /** 72 hours in milliseconds — boundary between Tier 2 and Tier 3. */
        const val VERY_STALE_THRESHOLD_MS = 72L * 60 * 60 * 1000
    }
}
