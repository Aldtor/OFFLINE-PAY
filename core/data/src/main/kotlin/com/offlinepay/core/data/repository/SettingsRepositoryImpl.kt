package com.offlinepay.core.data.repository

import com.offlinepay.core.common.dispatchers.IoDispatcher
import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.common.result.appResultOf
import com.offlinepay.core.data.crypto.EncryptedPreferencesProvider
import com.offlinepay.core.data.db.dao.SettingsDao
import com.offlinepay.core.data.db.entity.SettingsEntity
import com.offlinepay.core.domain.error.DomainError
import com.offlinepay.core.domain.model.AppSettings
import com.offlinepay.core.domain.model.AppTheme
import com.offlinepay.core.domain.model.PaymentMethodType
import com.offlinepay.core.domain.model.RoutingPriorityConfig
import com.offlinepay.core.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production implementation of [SettingsRepository].
 *
 * Sensitive settings are stored in [EncryptedPreferencesProvider]:
 * - Manual payment method override
 * - Preferred SIM slot
 * - Routing priority config
 *
 * Non-sensitive settings are stored in [SettingsDao] (Room):
 * - Theme, Language, Onboarding complete flag
 *
 * An in-memory [MutableStateFlow] ensures reactive UI updates without polling.
 *
 * Design reference: Section 9.5 (SettingsRepository)
 * Requirements: Req 11
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dao: SettingsDao,
    private val encryptedPrefs: EncryptedPreferencesProvider,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SettingsRepository {

    companion object {
        // EncryptedPreferences keys (sensitive settings)
        private const val KEY_MANUAL_OVERRIDE = "manual_override"
        private const val KEY_PREFERRED_SIM = "preferred_sim_slot"
        private const val KEY_ROUTING_PRIORITY = "routing_priority_config"

        // Room settings table keys (non-sensitive settings)
        private const val KEY_THEME = "theme"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_ONBOARDING = "onboarding_complete"
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** Scope used to hydrate settings off the main thread on first construction. */
    private val initScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    // In-memory StateFlow for reactive settings observation.
    //
    // Seeded synchronously with safe defaults so construction never touches disk —
    // this @Singleton is created lazily on the caller's (often the main) thread, and
    // opening the SQLCipher DB + unwrapping the Keystore key here previously blocked
    // the UI thread (ANR/cold-start jank). Real values are loaded asynchronously on
    // [ioDispatcher] and pushed into the flow, which the reactive UI picks up.
    private val _settings = MutableStateFlow(defaultSettings())

    init {
        initScope.launch { _settings.value = loadInitialSettings() }
    }

    /** Safe defaults used until [loadInitialSettings] completes off the main thread. */
    private fun defaultSettings(): AppSettings = AppSettings(
        theme = AppTheme.SYSTEM,
        language = "en",
        isOnboardingComplete = false,
        preferredSimSlot = null,
        manualPaymentMethodOverride = null,
        routingPriorityConfig = RoutingPriorityConfig.default(),
    )

    /** Loads settings from both Room (non-sensitive) and EncryptedPreferences (sensitive). */
    private suspend fun loadInitialSettings(): AppSettings {
        val themeStr = runCatching { dao.get(KEY_THEME)?.value }.getOrNull()
        val language = runCatching { dao.get(KEY_LANGUAGE)?.value }.getOrNull() ?: "en"
        val onboarding = runCatching {
            dao.get(KEY_ONBOARDING)?.value?.toBooleanStrictOrNull()
        }.getOrNull() ?: false

        val theme = runCatching { AppTheme.valueOf(themeStr ?: AppTheme.SYSTEM.name) }
            .getOrDefault(AppTheme.SYSTEM)

        val manualOverride = encryptedPrefs.getString(KEY_MANUAL_OVERRIDE)
            ?.let { runCatching { PaymentMethodType.valueOf(it) }.getOrNull() }

        val preferredSim = encryptedPrefs.getInt(KEY_PREFERRED_SIM, -1)
            .takeIf { it >= 0 }

        val routingConfig = encryptedPrefs.getString(KEY_ROUTING_PRIORITY)
            ?.let { runCatching { json.decodeFromString<RoutingPriorityConfig>(it) }.getOrNull() }
            ?: RoutingPriorityConfig.default()

        return AppSettings(
            theme = theme,
            language = language,
            isOnboardingComplete = onboarding,
            preferredSimSlot = preferredSim,
            manualPaymentMethodOverride = manualOverride,
            routingPriorityConfig = routingConfig,
        )
    }

    // ── Core interface methods ────────────────────────────────────────────────

    override fun getSettings(): Flow<AppSettings> = _settings.asStateFlow()

    override suspend fun updateSettings(
        update: AppSettings.() -> AppSettings,
    ): AppResult<Unit, DomainError.StorageError> = withContext(ioDispatcher) {
        appResultOf(
            errorMapper = { DomainError.StorageError.DatabaseError("updateSettings", it.message ?: "unknown") },
        ) {
            val current = _settings.value
            val updated = current.update()
            // Persist changed non-sensitive settings to Room
            if (updated.theme != current.theme) {
                dao.put(SettingsEntity(KEY_THEME, updated.theme.name, System.currentTimeMillis()))
            }
            if (updated.language != current.language) {
                dao.put(SettingsEntity(KEY_LANGUAGE, updated.language, System.currentTimeMillis()))
            }
            if (updated.isOnboardingComplete != current.isOnboardingComplete) {
                dao.put(SettingsEntity(KEY_ONBOARDING, updated.isOnboardingComplete.toString(), System.currentTimeMillis()))
            }
            // Persist changed sensitive settings to EncryptedPreferences
            if (updated.manualPaymentMethodOverride != current.manualPaymentMethodOverride) {
                val override = updated.manualPaymentMethodOverride
                if (override != null) {
                    encryptedPrefs.putString(KEY_MANUAL_OVERRIDE, override.name)
                } else {
                    encryptedPrefs.remove(KEY_MANUAL_OVERRIDE)
                }
            }
            if (updated.preferredSimSlot != current.preferredSimSlot) {
                val slot = updated.preferredSimSlot
                if (slot != null) {
                    encryptedPrefs.putInt(KEY_PREFERRED_SIM, slot)
                } else {
                    encryptedPrefs.remove(KEY_PREFERRED_SIM)
                }
            }
            if (updated.routingPriorityConfig != current.routingPriorityConfig) {
                encryptedPrefs.putString(KEY_ROUTING_PRIORITY, json.encodeToString(updated.routingPriorityConfig))
            }
            _settings.value = updated
        }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T> getSetting(
        key: String,
        default: T,
    ): AppResult<T, DomainError.StorageError> = withContext(ioDispatcher) {
        appResultOf(
            errorMapper = { DomainError.StorageError.DatabaseError("getSetting", it.message ?: "unknown") },
        ) {
            val entity = dao.get(key)
            if (entity == null) {
                default
            } else {
                // Attempt to cast stored String to the default's type T
                val value: Any = when (default) {
                    is Boolean -> entity.value.toBooleanStrictOrNull() ?: default
                    is Int -> entity.value.toIntOrNull() ?: default
                    is Long -> entity.value.toLongOrNull() ?: default
                    is Float -> entity.value.toFloatOrNull() ?: default
                    else -> entity.value
                }
                value as T
            }
        }
    }

    override suspend fun <T> putSetting(
        key: String,
        value: T,
    ): AppResult<Unit, DomainError.StorageError> = withContext(ioDispatcher) {
        appResultOf(
            errorMapper = { DomainError.StorageError.DatabaseError("putSetting", it.message ?: "unknown") },
        ) {
            dao.put(SettingsEntity(key, value.toString(), System.currentTimeMillis()))
        }
    }

    override suspend fun get(): AppSettings = _settings.value

    // ── Routing (EncryptedSharedPreferences) ──────────────────────────────────

    override suspend fun setRoutingPriority(config: RoutingPriorityConfig) {
        encryptedPrefs.putString(KEY_ROUTING_PRIORITY, json.encodeToString(config))
        _settings.update { it.copy(routingPriorityConfig = config) }
    }

    override suspend fun setManualOverride(method: PaymentMethodType?) {
        if (method != null) {
            encryptedPrefs.putString(KEY_MANUAL_OVERRIDE, method.name)
        } else {
            encryptedPrefs.remove(KEY_MANUAL_OVERRIDE)
        }
        _settings.update { it.copy(manualPaymentMethodOverride = method) }
    }

    override suspend fun setPreferredSimSlot(slotIndex: Int?) {
        if (slotIndex != null) {
            encryptedPrefs.putInt(KEY_PREFERRED_SIM, slotIndex)
        } else {
            encryptedPrefs.remove(KEY_PREFERRED_SIM)
        }
        _settings.update { it.copy(preferredSimSlot = slotIndex) }
    }

    // ── Non-sensitive settings (Room settings table) ──────────────────────────

    override suspend fun setTheme(theme: AppTheme) = withContext(ioDispatcher) {
        dao.put(SettingsEntity(KEY_THEME, theme.name, System.currentTimeMillis()))
        _settings.update { it.copy(theme = theme) }
    }

    override suspend fun setLanguage(language: String) = withContext(ioDispatcher) {
        dao.put(SettingsEntity(KEY_LANGUAGE, language, System.currentTimeMillis()))
        _settings.update { it.copy(language = language) }
    }

    override suspend fun setOnboardingComplete(complete: Boolean) = withContext(ioDispatcher) {
        dao.put(SettingsEntity(KEY_ONBOARDING, complete.toString(), System.currentTimeMillis()))
        _settings.update { it.copy(isOnboardingComplete = complete) }
    }

    // ── Legacy alias ──────────────────────────────────────────────────────────

    override fun observe(): Flow<AppSettings> = getSettings()
}
