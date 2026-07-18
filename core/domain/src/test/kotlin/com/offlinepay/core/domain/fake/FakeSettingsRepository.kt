package com.offlinepay.core.domain.fake

import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.domain.error.DomainError
import com.offlinepay.core.domain.model.AppSettings
import com.offlinepay.core.domain.model.AppTheme
import com.offlinepay.core.domain.model.PaymentMethodType
import com.offlinepay.core.domain.model.RoutingPriorityConfig
import com.offlinepay.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory fake [SettingsRepository] for unit tests.
 */
class FakeSettingsRepository : SettingsRepository {

    private val _settings = MutableStateFlow(AppSettings())

    var updateError: DomainError.StorageError? = null

    // ── Primary interface methods ─────────────────────────────────────────────

    override fun getSettings(): Flow<AppSettings> = _settings

    override suspend fun updateSettings(
        update: AppSettings.() -> AppSettings,
    ): AppResult<Unit, DomainError.StorageError> {
        updateError?.let { return AppResult.Failure(it) }
        _settings.value = _settings.value.update()
        return AppResult.Success(Unit)
    }

    override suspend fun <T> getSetting(
        key: String,
        default: T,
    ): AppResult<T, DomainError.StorageError> = AppResult.Success(default)

    override suspend fun <T> putSetting(
        key: String,
        value: T,
    ): AppResult<Unit, DomainError.StorageError> = AppResult.Success(Unit)

    override suspend fun get(): AppSettings = _settings.value

    override suspend fun setRoutingPriority(config: RoutingPriorityConfig) {
        _settings.value = _settings.value.copy(routingPriorityConfig = config)
    }

    override suspend fun setManualOverride(method: PaymentMethodType?) {
        _settings.value = _settings.value.copy(manualPaymentMethodOverride = method)
    }

    override suspend fun setPreferredSimSlot(slotIndex: Int?) {
        _settings.value = _settings.value.copy(preferredSimSlot = slotIndex)
    }

    override suspend fun setTheme(theme: AppTheme) {
        _settings.value = _settings.value.copy(theme = theme)
    }

    override suspend fun setLanguage(language: String) {
        _settings.value = _settings.value.copy(language = language)
    }

    override suspend fun setOnboardingComplete(complete: Boolean) {
        _settings.value = _settings.value.copy(isOnboardingComplete = complete)
    }
}
