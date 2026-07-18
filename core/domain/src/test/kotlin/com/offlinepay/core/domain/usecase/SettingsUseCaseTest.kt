package com.offlinepay.core.domain.usecase

import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.domain.error.DomainError
import com.offlinepay.core.domain.fake.FakeSettingsRepository
import com.offlinepay.core.domain.model.AppSettings
import com.offlinepay.core.domain.model.AppTheme
import com.offlinepay.core.domain.model.PaymentMethodType
import com.offlinepay.core.domain.usecase.settings.GetSettingsUseCase
import com.offlinepay.core.domain.usecase.settings.UpdateSettingsUseCase
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [GetSettingsUseCase] and [UpdateSettingsUseCase] — Task 5.5.
 */
class SettingsUseCaseTest {

    private lateinit var repository: FakeSettingsRepository
    private lateinit var getSettings: GetSettingsUseCase
    private lateinit var updateSettings: UpdateSettingsUseCase

    @BeforeEach
    fun setUp() {
        repository = FakeSettingsRepository()
        getSettings = GetSettingsUseCase(repository)
        updateSettings = UpdateSettingsUseCase(repository)
    }

    // ── GetSettingsUseCase ────────────────────────────────────────────────────

    @Test
    fun `getSettings returns default settings on first call`() = runTest {
        val settings = getSettings().first()
        settings shouldBe AppSettings()
    }

    @Test
    fun `getSettings flow emits updated value after change`() = runTest {
        // Initial value
        getSettings().first().theme shouldBe AppTheme.SYSTEM

        // Update via repository directly
        repository.setTheme(AppTheme.DARK)

        // Flow should emit the new value
        getSettings().first().theme shouldBe AppTheme.DARK
    }

    // ── UpdateSettingsUseCase — invoke operator ────────────────────────────────

    @Test
    fun `invoke operator updates settings via lambda`() = runTest {
        val result = updateSettings { copy(theme = AppTheme.LIGHT) }
        result.shouldBeInstanceOf<AppResult.Success<Unit>>()
        getSettings().first().theme shouldBe AppTheme.LIGHT
    }

    @Test
    fun `invoke operator can update multiple fields atomically`() = runTest {
        val result = updateSettings {
            copy(theme = AppTheme.DARK, language = "hi", isOnboardingComplete = true)
        }
        result.shouldBeInstanceOf<AppResult.Success<Unit>>()

        val settings = getSettings().first()
        settings.theme shouldBe AppTheme.DARK
        settings.language shouldBe "hi"
        settings.isOnboardingComplete shouldBe true
    }

    @Test
    fun `invoke operator returns Failure when repository fails`() = runTest {
        repository.updateError = DomainError.StorageError.DatabaseError("updateSettings", "write failed")

        val result = updateSettings { copy(theme = AppTheme.DARK) }
        result.shouldBeInstanceOf<AppResult.Failure<DomainError.StorageError>>()
    }

    @Test
    fun `invoke operator does not persist when repository fails`() = runTest {
        repository.updateError = DomainError.StorageError.DatabaseError("updateSettings", "write failed")
        updateSettings { copy(theme = AppTheme.DARK) }

        // Settings should remain at default since the write failed
        getSettings().first().theme shouldBe AppTheme.SYSTEM
    }

    // ── UpdateSettingsUseCase — named convenience methods ────────────────────

    @Test
    fun `setTheme convenience method updates theme`() = runTest {
        updateSettings.setTheme(AppTheme.DARK)
        getSettings().first().theme shouldBe AppTheme.DARK
    }

    @Test
    fun `setLanguage convenience method updates language`() = runTest {
        updateSettings.setLanguage("hi")
        getSettings().first().language shouldBe "hi"
    }

    @Test
    fun `setOnboardingComplete marks onboarding as done`() = runTest {
        updateSettings.setOnboardingComplete(true)
        getSettings().first().isOnboardingComplete shouldBe true
    }

    @Test
    fun `setManualOverride persists override`() = runTest {
        updateSettings.setManualOverride(PaymentMethodType.PAY123)
        getSettings().first().manualPaymentMethodOverride shouldBe PaymentMethodType.PAY123
    }

    @Test
    fun `setManualOverride with null clears override`() = runTest {
        updateSettings.setManualOverride(PaymentMethodType.PAY123)
        updateSettings.setManualOverride(null)
        getSettings().first().manualPaymentMethodOverride shouldBe null
    }

    @Test
    fun `setPreferredSimSlot persists slot index`() = runTest {
        updateSettings.setPreferredSimSlot(1)
        getSettings().first().preferredSimSlot shouldBe 1
    }

    @Test
    fun `setPreferredSimSlot with null clears preference`() = runTest {
        updateSettings.setPreferredSimSlot(0)
        updateSettings.setPreferredSimSlot(null)
        getSettings().first().preferredSimSlot shouldBe null
    }
}
