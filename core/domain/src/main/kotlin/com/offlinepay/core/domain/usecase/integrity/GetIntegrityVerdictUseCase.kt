package com.offlinepay.core.domain.usecase.integrity


import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.domain.error.DomainError
import com.offlinepay.core.domain.model.IntegrityVerdict
import com.offlinepay.core.domain.repository.IntegrityRepository

/**
 * Bundles the cached [IntegrityVerdict] with its staleness in milliseconds so
 * callers can apply the 5-tier display logic without a second repository call.
 *
 * Tier mapping:
 * - ageMs < 24h AND verdict = PASS → Tier 1 (allow, no warning)
 * - 24h ≤ ageMs < 72h AND verdict = PASS → Tier 2 (allow, dismissible notice)
 * - ageMs ≥ 72h → Tier 3 (allow, persistent banner)
 * - verdict = FAIL → Tier 4 (hard block)
 * - verdict = null → Tier 3 (no cache, persistent banner)
 *
 * Design reference: Section 8.2 (5-tier caching strategy)
 * Requirements: Req 9.2–9.4
 *
 * @param verdict The cached [IntegrityVerdict], or null if no verdict has been stored yet.
 * @param ageMs   Age of the cached verdict in milliseconds.
 *                [Long.MAX_VALUE] when [verdict] is null (treat as maximally stale).
 */
data class IntegrityVerdictResult(
    val verdict: IntegrityVerdict?,
    val ageMs: Long,
)

/**
 * Use case for retrieving the cached Play Integrity verdict and its staleness.
 *
 * Used by `SecurityGuard` and `DashboardViewModel` to determine which integrity
 * tier banner (if any) to display.
 *
 * Design reference: Section 8.2 (5-tier caching strategy), Section 3.3 (Use Cases)
 * Requirements: Req 9.2–9.4 (tiered display based on verdict age)
 */
fun interface GetIntegrityVerdictUseCase {

    /**
     * Returns the cached verdict wrapped in an [IntegrityVerdictResult].
     *
     * @return [AppResult.Success] containing [IntegrityVerdictResult]
     *         (verdict may be null if never cached).
     *         [AppResult.Failure] with [DomainError.StorageError] on read failure.
     */
    suspend operator fun invoke(): AppResult<IntegrityVerdictResult, DomainError.StorageError>
}

/**
 * Default implementation that composes [IntegrityRepository.getVerdict] and
 * [IntegrityRepository.getVerdictAgeMs] into a single [IntegrityVerdictResult].
 *
 * Bind this in your Hilt module:
 * ```kotlin
 * @Provides
 * fun provideGetIntegrityVerdictUseCase(
 *     repo: IntegrityRepository,
 * ): GetIntegrityVerdictUseCase = DefaultGetIntegrityVerdictUseCase(repo)
 * ```
 *
 * @param integrityRepository Injected via Hilt.
 */
class DefaultGetIntegrityVerdictUseCase(
    private val integrityRepository: IntegrityRepository,
) : GetIntegrityVerdictUseCase {

    override suspend fun invoke(): AppResult<IntegrityVerdictResult, DomainError.StorageError> {
        return when (val verdictResult = integrityRepository.getVerdict()) {
            is AppResult.Success -> {
                val ageMs = if (verdictResult.data != null) {
                    integrityRepository.getVerdictAgeMs()
                } else {
                    Long.MAX_VALUE
                }
                AppResult.Success(
                    IntegrityVerdictResult(
                        verdict = verdictResult.data,
                        ageMs = ageMs,
                    )
                )
            }
            is AppResult.Failure -> AppResult.Failure(verdictResult.error)
            else -> {
                // AppResult.Loading is not expected from a repository read; treat as no-cache.
                AppResult.Success(IntegrityVerdictResult(verdict = null, ageMs = Long.MAX_VALUE))
            }
        }
    }
}
