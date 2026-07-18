package com.offlinepay.core.domain.fake

import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.domain.error.DomainError
import com.offlinepay.core.domain.model.IntegrityVerdict
import com.offlinepay.core.domain.repository.IntegrityRepository

/**
 * In-memory fake [IntegrityRepository] for unit tests.
 */
class FakeIntegrityRepository : IntegrityRepository {

    private var cachedVerdict: IntegrityVerdict? = null
    private var cachedAtMs: Long = 0L
    private var currentTimeMs: Long = System.currentTimeMillis()

    var readError: DomainError.StorageError? = null

    fun setCurrentTime(timeMs: Long) { currentTimeMs = timeMs }

    // ── Primary interface methods ─────────────────────────────────────────────

    override suspend fun saveVerdict(
        verdict: IntegrityVerdict,
    ): AppResult<Unit, DomainError.StorageError> {
        cachedVerdict = verdict
        cachedAtMs = currentTimeMs
        return AppResult.Success(Unit)
    }

    override suspend fun getVerdict(): AppResult<IntegrityVerdict?, DomainError.StorageError> {
        readError?.let { return AppResult.Failure(it) }
        return AppResult.Success(cachedVerdict)
    }

    override suspend fun getVerdictAgeMs(): Long =
        if (cachedVerdict != null) currentTimeMs - cachedAtMs else 0L

    override suspend fun getCachedVerdict(): Pair<IntegrityVerdict?, Long> =
        Pair(cachedVerdict, if (cachedVerdict != null) currentTimeMs - cachedAtMs else Long.MAX_VALUE)

    override suspend fun clearVerdict() {
        cachedVerdict = null
        cachedAtMs = 0L
    }
}
