package com.offlinepay.core.data.repository

import com.offlinepay.core.common.dispatchers.IoDispatcher
import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.common.result.appResultOf
import com.offlinepay.core.data.crypto.EncryptedPreferencesProvider
import com.offlinepay.core.domain.error.DomainError
import com.offlinepay.core.domain.model.IntegrityVerdict
import com.offlinepay.core.domain.repository.IntegrityRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production implementation of [IntegrityRepository].
 *
 * Caches the Play Integrity verdict in [EncryptedSharedPreferences] with a timestamp.
 * Verdict and timestamp are stored as JSON and a Long respectively.
 *
 * Design reference: Section 8.2 (5-tier caching strategy)
 * Requirements: Req 9.1–9.6
 */
@Singleton
class IntegrityRepositoryImpl @Inject constructor(
    private val encryptedPrefs: EncryptedPreferencesProvider,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : IntegrityRepository {

    companion object {
        private const val KEY_VERDICT_JSON = "integrity_verdict_json"
        private const val KEY_VERDICT_TIMESTAMP = "integrity_verdict_timestamp"
    }

    private val json = Json { ignoreUnknownKeys = true }

    // ── Core interface methods ────────────────────────────────────────────────

    override suspend fun saveVerdict(
        verdict: IntegrityVerdict,
    ): AppResult<Unit, DomainError.StorageError> = withContext(ioDispatcher) {
        appResultOf(
            errorMapper = { DomainError.StorageError.DatabaseError("saveVerdict", it.message ?: "unknown") },
        ) {
            encryptedPrefs.putString(KEY_VERDICT_JSON, json.encodeToString(verdict))
            encryptedPrefs.putLong(KEY_VERDICT_TIMESTAMP, verdict.cachedAtMs)
        }
    }

    override suspend fun getVerdict(): AppResult<IntegrityVerdict?, DomainError.StorageError> =
        withContext(ioDispatcher) {
            appResultOf(
                errorMapper = { DomainError.StorageError.DatabaseError("getVerdict", it.message ?: "unknown") },
            ) {
                val verdictJson = encryptedPrefs.getString(KEY_VERDICT_JSON)
                    ?: return@appResultOf null
                runCatching { json.decodeFromString<IntegrityVerdict>(verdictJson) }.getOrNull()
            }
        }

    override suspend fun getVerdictAgeMs(): Long = withContext(ioDispatcher) {
        val cachedAt = encryptedPrefs.getLong(KEY_VERDICT_TIMESTAMP, 0L)
        if (cachedAt == 0L) 0L else System.currentTimeMillis() - cachedAt
    }

    // ── Legacy/convenience methods ────────────────────────────────────────────

    override suspend fun getCachedVerdict(): Pair<IntegrityVerdict?, Long> = withContext(ioDispatcher) {
        val verdictJson = encryptedPrefs.getString(KEY_VERDICT_JSON)
            ?: return@withContext Pair(null, Long.MAX_VALUE)
        val cachedAt = encryptedPrefs.getLong(KEY_VERDICT_TIMESTAMP, 0L)
        val verdict = runCatching { json.decodeFromString<IntegrityVerdict>(verdictJson) }.getOrNull()
            ?: return@withContext Pair(null, Long.MAX_VALUE)
        val ageMs = System.currentTimeMillis() - cachedAt
        Pair(verdict, ageMs)
    }

    override suspend fun cacheVerdict(verdict: IntegrityVerdict) {
        saveVerdict(verdict)
    }

    override suspend fun clearVerdict() = withContext(ioDispatcher) {
        encryptedPrefs.remove(KEY_VERDICT_JSON)
        encryptedPrefs.remove(KEY_VERDICT_TIMESTAMP)
    }
}
