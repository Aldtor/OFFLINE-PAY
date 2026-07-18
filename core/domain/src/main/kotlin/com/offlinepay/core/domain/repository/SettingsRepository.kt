package com.offlinepay.core.domain.repository

import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.domain.error.DomainError
import com.offlinepay.core.domain.model.AppSettings
import com.offlinepay.core.domain.model.AppTheme
import com.offlinepay.core.domain.model.PaymentMethodType
import com.offlinepay.core.domain.model.RoutingPriorityConfig
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for application settings persistence.
 *
 * Sensitive settings (routing config, SIM preference, manual override) are stored
 * in `EncryptedSharedPreferences`. Non-sensitive settings (theme, language,
 * onboarding status) are in the Room `settings` table.
 *
 * All settings changes are persisted immediately (Req 11.7).
 * Implemented by `SettingsRepositoryImpl` in `:core:data`.
 *
 * Design reference: Section 9.5 (SettingsRepository interface)
 * Requirements: Req 11 (Settings feature)
 */
interface SettingsRepository {

    /**
     * Returns a [Flow] of the current [AppSettings].
     * Emits a new value whenever any setting changes.
     *
     * Requirements: Req 11 (real-time settings observation)
     */
    fun getSettings(): Flow<AppSettings>

    /**
     * Atomically updates the current settings by applying [update] to the existing
     * [AppSettings] snapshot and persisting the result.
     *
     * Usage:
     * ```kotlin
     * settingsRepository.updateSettings { copy(theme = AppTheme.DARK) }
     * ```
     *
     * @param update A lambda that receives the current [AppSettings] and returns the updated one.
     * @return [AppResult.Success] on success.
     *         [AppResult.Failure] with [DomainError.StorageError] on persistence failure.
     *
     * Requirements: Req 11.7 (settings persist immediately)
     */
    suspend fun updateSettings(
        update: AppSettings.() -> AppSettings,
    ): AppResult<Unit, DomainError.StorageError>

    /**
     * Retrieves the value of a single settings entry identified by [key].
     * Returns [default] if the key is not present in the store.
     *
     * @param key   The settings key string.
     * @param default The value to return when the key is absent.
     * @return [AppResult.Success] with the stored or default value.
     *         [AppResult.Failure] with [DomainError.StorageError] on read failure.
     *
     * Requirements: Req 11 (typed settings access)
     */
    suspend fun <T> getSetting(key: String, default: T): AppResult<T, DomainError.StorageError>

    /**
     * Persists a single settings entry identified by [key].
     *
     * @param key   The settings key string.
     * @param value The value to store. Must be a type supported by the underlying store
     *              (Boolean, Int, Long, Float, String, Set<String>).
     * @return [AppResult.Success] on success.
     *         [AppResult.Failure] with [DomainError.StorageError] on write failure.
     *
     * Requirements: Req 11 (typed settings write)
     */
    suspend fun <T> putSetting(key: String, value: T): AppResult<Unit, DomainError.StorageError>

    // ── Typed convenience methods ─────────────────────────────────────────────
    // These delegate to updateSettings for atomic persistence.

    /**
     * Returns the current settings snapshot as a one-shot value.
     * Prefer [getSettings] (Flow) for reactive consumers.
     */
    suspend fun get(): AppSettings

    // ── Routing (stored in EncryptedSharedPreferences) ──────────────────────

    /** Persists the routing priority configuration immediately. */
    suspend fun setRoutingPriority(config: RoutingPriorityConfig)

    /**
     * Persists the user's manual payment method override.
     * Pass null to clear the override and revert to automatic routing.
     *
     * Requirements: Req 4.5 (manual override persisted in EncryptedPreferences)
     */
    suspend fun setManualOverride(method: PaymentMethodType?)

    // ── SIM preference (stored in EncryptedSharedPreferences) ───────────────

    /**
     * Persists the preferred SIM slot for payments.
     * Pass null to clear the preference and prompt per-transaction.
     *
     * Requirements: Req 11.4 (preferred SIM preference)
     */
    suspend fun setPreferredSimSlot(slotIndex: Int?)

    // ── Non-sensitive settings (stored in Room settings table) ───────────────

    /**
     * Updates the display theme.
     *
     * Requirements: Req 11.2 (Light/Dark/System themes)
     */
    suspend fun setTheme(theme: AppTheme)

    /**
     * Updates the display language.
     *
     * Requirements: Req 11.1 (language switching without restart)
     */
    suspend fun setLanguage(language: String)

    /**
     * Marks the onboarding flow as complete so it is not shown again.
     *
     * Requirements: Req 10.4 (don't show onboarding again after completion)
     */
    suspend fun setOnboardingComplete(complete: Boolean)

    // ── Legacy compatibility alias ─────────────────────────────────────────

    /**
     * Alias for [getSettings]. Retained for use-case layer compatibility.
     */
    fun observe(): Flow<AppSettings> = getSettings()
}
