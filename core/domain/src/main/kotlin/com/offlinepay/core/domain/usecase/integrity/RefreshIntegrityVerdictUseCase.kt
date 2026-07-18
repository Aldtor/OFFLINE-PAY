package com.offlinepay.core.domain.usecase.integrity

import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.domain.error.DomainError
import com.offlinepay.core.domain.model.IntegrityVerdict

/**
 * Use case for requesting a fresh Play Integrity API verdict and caching it.
 *
 * Called by [IntegrityRefreshWorker] (WorkManager, Tier 5) when internet becomes available,
 * and by the Settings screen when the user taps "Refresh integrity check".
 *
 * Design reference: Section 8.2 (Tier 5 — silent background refresh)
 * Requirements: Req 9.6 (silently refresh when internet available)
 */
fun interface RefreshIntegrityVerdictUseCase {

    /**
     * Fetches a fresh verdict from the Play Integrity API and caches it.
     *
     * @return [AppResult.Success] with the fresh [IntegrityVerdict].
     *         [AppResult.Failure] with [DomainError.SecurityError.IntegrityFailed] on API failure.
     */
    suspend operator fun invoke(): AppResult<IntegrityVerdict, DomainError.SecurityError>
}
