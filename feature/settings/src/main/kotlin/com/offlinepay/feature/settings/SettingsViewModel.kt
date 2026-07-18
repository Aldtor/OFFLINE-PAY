package com.offlinepay.feature.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.domain.model.AppSettings
import com.offlinepay.core.domain.model.AppTheme
import com.offlinepay.core.domain.model.PaymentMethodType
import com.offlinepay.core.domain.repository.SettingsRepository
import com.offlinepay.core.domain.usecase.integrity.RefreshIntegrityVerdictUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
// UI state
// ─────────────────────────────────────────────────────────────────────────────

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val isLoading: Boolean = true,
    val isRefreshingIntegrity: Boolean = false,
    val appVersion: String = "1.0.0",
)

// ─────────────────────────────────────────────────────────────────────────────
// One-shot events
// ─────────────────────────────────────────────────────────────────────────────

sealed class SettingsEvent {
    data class NavigateTo(val route: String) : SettingsEvent()
    data class ShowMessage(val message: String) : SettingsEvent()
}

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

/**
 * ViewModel for all Settings screens.
 *
 * Collects current settings and provides actions for updating them.
 * All changes persist immediately via [SettingsRepository] (Req 11.7).
 *
 * Design reference: Section 10.2 (SettingsViewModel)
 * Requirements: Req 11.1–11.9
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val refreshIntegrityVerdictUseCase: RefreshIntegrityVerdictUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        observeSettings()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.getSettings().collect { settings ->
                _uiState.value = _uiState.value.copy(
                    settings = settings,
                    isLoading = false,
                )
            }
        }
    }

    // ── Theme ────────────────────────────────────────────────────────────────

    fun onThemeChanged(theme: AppTheme) {
        viewModelScope.launch {
            settingsRepository.setTheme(theme)
        }
    }

    // ── Language ──────────────────────────────────────────────────────────────

    fun onLanguageChanged(language: String) {
        viewModelScope.launch {
            settingsRepository.setLanguage(language)
            // Apply locale change without restart via AppCompatDelegate (Req 11.1)
            val localeList = LocaleListCompat.forLanguageTags(language)
            AppCompatDelegate.setApplicationLocales(localeList)
            _events.send(SettingsEvent.ShowMessage("Language changed to ${if (language == "hi") "Hindi" else "English"}"))
        }
    }

    // ── Payment method override ──────────────────────────────────────────────

    fun onManualOverrideChanged(method: PaymentMethodType?) {
        viewModelScope.launch {
            settingsRepository.setManualOverride(method)
        }
    }

    // ── SIM preference ───────────────────────────────────────────────────────

    fun onPreferredSimChanged(slotIndex: Int?) {
        viewModelScope.launch {
            settingsRepository.setPreferredSimSlot(slotIndex)
        }
    }

    // ── Routing priority ─────────────────────────────────────────────────────

    fun onRoutingPriorityChanged(
        config: com.offlinepay.core.domain.model.RoutingPriorityConfig,
    ) {
        viewModelScope.launch {
            settingsRepository.setRoutingPriority(config)
        }
    }

    // ── Integrity ────────────────────────────────────────────────────────────

    fun onRefreshIntegrity() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshingIntegrity = true)
            when (refreshIntegrityVerdictUseCase()) {
                is AppResult.Success -> {
                    _uiState.value = _uiState.value.copy(isRefreshingIntegrity = false)
                    _events.send(SettingsEvent.ShowMessage("Integrity check refreshed"))
                }
                is AppResult.Failure -> {
                    _uiState.value = _uiState.value.copy(isRefreshingIntegrity = false)
                    _events.send(SettingsEvent.ShowMessage("Integrity check failed. Please try when online."))
                }
                else -> Unit
            }
        }
    }

    // ── Navigation ───────────────────────────────────────────────────────────

    fun onNavigateTo(route: String) {
        viewModelScope.launch { _events.send(SettingsEvent.NavigateTo(route)) }
    }
}
