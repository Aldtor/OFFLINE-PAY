package com.offlinepay.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinepay.core.domain.usecase.settings.UpdateSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val updateSettingsUseCase: UpdateSettingsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<OnboardingUiEvent>()
    val events: SharedFlow<OnboardingUiEvent> = _events.asSharedFlow()

    fun onAction(action: OnboardingUiAction) = when (action) {
        OnboardingUiAction.NextStep -> nextStep()
        OnboardingUiAction.PreviousStep -> previousStep()
        OnboardingUiAction.CompleteOnboarding -> completeOnboarding()
        is OnboardingUiAction.CameraPermissionResult ->
            _uiState.update { it.copy(isCameraGranted = action.granted) }
        is OnboardingUiAction.PhonePermissionResult ->
            _uiState.update { it.copy(isPhoneGranted = action.granted) }
        is OnboardingUiAction.ReadPhoneStatePermissionResult ->
            _uiState.update { it.copy(isReadPhoneStateGranted = action.granted) }
    }

    private fun nextStep() {
        val current = _uiState.value
        if (current.currentStep < OnboardingStep.entries.lastIndex) {
            _uiState.update { it.copy(currentStep = it.currentStep + 1) }
        }
    }

    private fun previousStep() {
        val current = _uiState.value
        if (current.currentStep > 0) {
            _uiState.update { it.copy(currentStep = it.currentStep - 1) }
        }
    }

    private fun completeOnboarding() {
        viewModelScope.launch {
            updateSettingsUseCase { copy(isOnboardingComplete = true) }
            _events.emit(OnboardingUiEvent.NavigateToDashboard)
        }
    }
}

data class OnboardingUiState(
    val currentStep: Int = 0,
    val isCameraGranted: Boolean = false,
    val isPhoneGranted: Boolean = false,
    val isReadPhoneStateGranted: Boolean = false,
)

enum class OnboardingStep { WELCOME, OFFLINE_EXPLANATION, PERMISSIONS, COMPLETE }

sealed class OnboardingUiEvent {
    data object NavigateToDashboard : OnboardingUiEvent()
}

sealed class OnboardingUiAction {
    data object NextStep : OnboardingUiAction()
    data object PreviousStep : OnboardingUiAction()
    data object CompleteOnboarding : OnboardingUiAction()
    data class CameraPermissionResult(val granted: Boolean) : OnboardingUiAction()
    data class PhonePermissionResult(val granted: Boolean) : OnboardingUiAction()
    data class ReadPhoneStatePermissionResult(val granted: Boolean) : OnboardingUiAction()
}
