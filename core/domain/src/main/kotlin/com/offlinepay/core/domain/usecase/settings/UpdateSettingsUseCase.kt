package com.offlinepay.core.domain.usecase.settings

import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.domain.error.DomainError
import com.offlinepay.core.domain.model.AppSettings
import com.offlinepay.core.domain.model.AppTheme
import com.offlinepay.core.domain.model.PaymentMethodType
import com.offlinepay.core.domain.model.RoutingPriorityConfig
import com.offlinepay.core.domain.repository.SettingsRepository

/**
 * Use case for atomically updating application settings.
 *
 * The primary entry point is [invoke], which takes a lambda that transforms the
 * current [AppSettings] snapshot into the desired new state. All updates are
 * persisted immediately (Req 11.7). The [GetSettingsUseCase] flow emits after
 * each successful update.
 *
 * Named convenience methods are provided for the most common single-field updates;
 * they all delegate to [invoke] so callers get a consistent `AppResult`.
 *
 * Design reference: Section 3.3 (Use Cases), Section 9.5 (SettingsRepository)
 * Requirements: Req 11.1–11.9
 *
 * @param settingsRepository Injected via Hilt.
 */
class UpdateSettingsUseCase(
    private val settingsRepository: SettingsRepository,
) {

    /**
     * Atomically applies [update] to the current [AppSettings] and persists the result.
     *
     * Usage:
     * ```kotlin
     * updateSettingsUseCase { copy(theme = AppTheme.DARK) }
     * ```
     *
     * @param update Lambda that receives the current settings and returns the new settings.
     * @return [AppResult.Success] on success.
     *         [AppResult.Failure] with [DomainError.StorageError] on persistence failure.
     *
     * Requirements: Req 11.7 (settings persist immediately)
     */
    suspend operator fun invoke(
        update: AppSettings.() -> AppSettings,
    ): AppResult<Unit, DomainError.StorageError> =
        settingsRepository.updateSettings(update)

    // ── Named convenience methods — delegate to invoke for a consistent AppResult ──

    /** Updates the routing priority configuration (Req 11.8). */
    suspend fun setRoutingPriority(config: RoutingPriorityConfig) =
        settingsRepository.setRoutingPriority(config)

    /**
     * Sets or clears the manual payment method override (Req 11.9, Req 4.5).
     * Pass null to restore automatic operator-based routing.
     */
    suspend fun setManualOverride(method: PaymentMethodType?) =
        settingsRepository.setManualOverride(method)

    /** Sets the preferred SIM slot for payments (Req 11.4). Pass null to prompt per-transaction. */
    suspend fun setPreferredSimSlot(slotIndex: Int?) =
        settingsRepository.setPreferredSimSlot(slotIndex)

    /** Updates the display theme (Req 11.2). */
    suspend fun setTheme(theme: AppTheme) =
        settingsRepository.setTheme(theme)

    /** Updates the display language without requiring an app restart (Req 11.1). */
    suspend fun setLanguage(language: String) =
        settingsRepository.setLanguage(language)

    /** Marks onboarding as complete so it is not shown again (Req 10.4). */
    suspend fun setOnboardingComplete(complete: Boolean) =
        settingsRepository.setOnboardingComplete(complete)
}
