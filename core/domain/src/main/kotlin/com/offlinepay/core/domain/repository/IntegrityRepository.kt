package com.offlinepay.core.domain.repository

import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.domain.error.DomainError
import com.offlinepay.core.domain.model.IntegrityVerdict

/**
 * Repository interface for caching Play Integrity API verdicts.
 *
 * Implements the offline-friendly 5-tier caching strategy from Design Section 8.2.
 * Verdicts are stored in `EncryptedSharedPreferences` — never in plaintext.
 * Implemented by `IntegrityRepositoryImpl` in `:core:data`.
 *
 * Design reference: Section 8.2 (Play Integrity — Offline-Friendly Strategy)
 * Requirements: Req 9.1–9.6
 */
interface IntegrityRepository {

    /**
     * Persists a fresh [IntegrityVerdict] to the encrypted cache.
     * The implementation must record the current timestamp alongside the verdict
     * so that [getVerdictAgeMs] can compute staleness accurately.
     *
     * @return [AppResult.Success] on success.
     *         [AppResult.Failure] with [DomainError.StorageError] on write failure.
     *
     * Requirements: Req 9.1 (cache verdict with timestamp)
     */
    suspend fun saveVerdict(
        verdict: IntegrityVerdict,
    ): AppResult<Unit, DomainError.StorageError>

    /**
     * Retrieves the cached [IntegrityVerdict].
     * Returns `null` data (not an error) if no verdict has been cached yet.
     *
     * Tier logic for callers:
     * - ageMs < 24h AND verdict.type == PASS → Tier 1: allow, no warning
     * - 24h ≤ ageMs < 72h AND verdict.type == PASS → Tier 2: allow, dismissible notice
     * - ageMs ≥ 72h or null → Tier 3: allow, persistent banner
     * - verdict.type == FAIL → Tier 4: hard block
     *
     * @return [AppResult.Success] with the cached [IntegrityVerdict], or null if absent.
     *         [AppResult.Failure] with [DomainError.StorageError] on read failure.
     *
     * Requirements: Req 9.2–9.5 (tiered display based on verdict and age)
     */
    suspend fun getVerdict(): AppResult<IntegrityVerdict?, DomainError.StorageError>

    /**
     * Returns the age of the cached verdict in milliseconds.
     * Computed as `System.currentTimeMillis() - cachedAtMs`.
     *
     * Returns `0L` if no verdict has been cached (callers treat this as "no cache"
     * by checking [getVerdict] for null first).
     *
     * Requirements: Req 9.2–9.4 (tiered display based on staleness)
     */
    suspend fun getVerdictAgeMs(): Long

    // ── Legacy / convenience methods kept for callers that used the old API ──

    /**
     * Returns the cached [IntegrityVerdict] and its age in milliseconds as a [Pair].
     *
     * @return Pair of (verdict, ageMs) where ageMs = currentTime - cachedAtMs.
     *         Returns (null, Long.MAX_VALUE) if no verdict has been cached.
     *
     * Prefer [getVerdict] + [getVerdictAgeMs] for new callers.
     */
    suspend fun getCachedVerdict(): Pair<IntegrityVerdict?, Long>

    /**
     * Alias for [saveVerdict] that ignores the [AppResult] and uses fire-and-forget semantics.
     * Retained for `IntegrityVerdictCache` compatibility.
     *
     * Prefer [saveVerdict] for new callers that need error feedback.
     */
    suspend fun cacheVerdict(verdict: IntegrityVerdict) {
        saveVerdict(verdict)
    }

    /**
     * Clears the cached verdict (e.g., after a failed device integrity event).
     */
    suspend fun clearVerdict()
}
