package com.offlinepay.core.security.integrity

import android.util.Base64
import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.domain.error.DomainError
import com.offlinepay.core.domain.model.IntegrityVerdict
import com.offlinepay.core.domain.usecase.integrity.RefreshIntegrityVerdictUseCase
import javax.inject.Inject

/**
 * Concrete implementation of [RefreshIntegrityVerdictUseCase].
 *
 * Calls [IntegrityApiClient] to fetch a fresh Play Integrity verdict,
 * parses it via [VerdictParser], and caches it via [IntegrityVerdictCache].
 *
 * Used by:
 * - [IntegrityRefreshWorker] (periodic 24h background refresh)
 * - [SettingsViewModel] ("Refresh integrity check" button in UI)
 *
 * Design reference: Section 8.2 (Tier 5 — silent background refresh), Section 15.3
 * Requirements: Req 9.1 (fresh verdict on launch), Req 9.6 (refresh when internet available)
 */
class RefreshIntegrityVerdictUseCaseImpl @Inject constructor(
    private val integrityApiClient: IntegrityApiClient,
    private val verdictParser: VerdictParser,
    private val integrityVerdictCache: IntegrityVerdictCache,
) : RefreshIntegrityVerdictUseCase {

    override suspend fun invoke(): AppResult<IntegrityVerdict, DomainError.SecurityError> {
        return try {
            val requestTimeMs = System.currentTimeMillis()
            val nonce = Base64.encodeToString(
                requestTimeMs.toString().toByteArray(),
                Base64.NO_WRAP,
            )
            val token = integrityApiClient.requestToken(nonce)
            val verdict = verdictParser.parse(token, requestTimeMs)
            integrityVerdictCache.cacheVerdict(verdict)
            AppResult.Success(verdict)
        } catch (e: Exception) {
            AppResult.Failure(
                DomainError.SecurityError.IntegrityFailed(
                    verdictDetails = e.message ?: "IntegrityApiClient failed",
                )
            )
        }
    }
}
