package com.offlinepay.core.domain.usecase.settings

import com.offlinepay.core.domain.model.AppSettings
import com.offlinepay.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use case for observing the current [AppSettings] as a reactive [Flow].
 *
 * ViewModels collect this flow to react to settings changes in real-time.
 * The flow emits a new value whenever any setting is updated.
 *
 * Design reference: Section 3.3 (Use Cases), Section 9.5 (SettingsRepository)
 * Requirements: Req 11 (Settings feature)
 *
 * @param settingsRepository Injected via Hilt.
 */
class GetSettingsUseCase(
    private val settingsRepository: SettingsRepository,
) {
    /**
     * Returns a [Flow] of the current [AppSettings].
     * Emits immediately with the current value, then on every change.
     */
    operator fun invoke(): Flow<AppSettings> = settingsRepository.observe()
}
