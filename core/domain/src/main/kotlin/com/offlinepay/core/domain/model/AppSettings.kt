package com.offlinepay.core.domain.model

import kotlinx.serialization.Serializable

/**
 * Application-level settings persisted by the [SettingsRepository].
 *
 * Sensitive settings (routing config, manual override, SIM preference) are stored
 * in `EncryptedSharedPreferences`. Non-sensitive settings (theme, language) are in
 * the Room `settings` table.
 *
 * Design reference: Section 9.5 (SettingsRepository interface)
 * Requirements: Req 11 (Settings feature)
 *
 * @param theme Selected display theme.
 * @param language BCP-47 language tag (e.g., `"en"`, `"hi"`).
 * @param isOnboardingComplete True once the user has completed first-launch onboarding.
 * @param preferredSimSlot User-preferred SIM slot for payments (null = prompt every time).
 * @param manualPaymentMethodOverride User-selected payment method override (null = auto-route).
 * @param routingPriorityConfig Per-operator routing priority configuration.
 */
@Serializable
data class AppSettings(
    val theme: AppTheme = AppTheme.SYSTEM,
    val language: String = "en",
    val isOnboardingComplete: Boolean = false,
    val preferredSimSlot: Int? = null,
    val manualPaymentMethodOverride: PaymentMethodType? = null,
    val routingPriorityConfig: RoutingPriorityConfig = RoutingPriorityConfig.default(),
)

/**
 * Display theme preference.
 *
 * Requirements: Req 11.2 (Light/Dark/System themes)
 */
@Serializable
enum class AppTheme {
    /** Use the system's current light/dark mode setting (Req 11.3). */
    SYSTEM,

    /** Force light mode. */
    LIGHT,

    /** Force dark mode. */
    DARK,
}

/**
 * Per-operator routing priority configuration for the [RoutingEngine].
 *
 * Stored in `EncryptedSharedPreferences` so user preferences survive app restarts.
 * Changes apply immediately for the next payment attempt without restart (Req 4.10).
 *
 * Design reference: Section 4.2 (Default Routing Priority Map)
 * Requirements: Req 4.2 (configurable priority list), Req 4.10 (immediate application)
 *
 * @param airtelPriority Ordered payment methods for Airtel SIMs.
 * @param viPriority Ordered payment methods for Vi (Vodafone Idea) SIMs.
 * @param bsnlPriority Ordered payment methods for BSNL SIMs.
 * @param jioPriority Ordered payment methods for Jio SIMs.
 * @param otherPriority Ordered payment methods for unknown/other operators.
 */
@Serializable
data class RoutingPriorityConfig(
    val airtelPriority: List<PaymentMethodType>,
    val viPriority: List<PaymentMethodType>,
    val bsnlPriority: List<PaymentMethodType>,
    val jioPriority: List<PaymentMethodType>,
    val otherPriority: List<PaymentMethodType>,
) {
    companion object {
        /**
         * Default routing priority per Design Section 4.2.
         * Airtel/Vi/BSNL → USSD first, then 123PAY.
         * Jio → 123PAY first, then USSD.
         * Other → USSD first, then 123PAY.
         */
        val DEFAULT: RoutingPriorityConfig
            get() = RoutingPriorityConfig(
                airtelPriority = listOf(PaymentMethodType.USSD, PaymentMethodType.PAY123),
                viPriority = listOf(PaymentMethodType.USSD, PaymentMethodType.PAY123),
                bsnlPriority = listOf(PaymentMethodType.USSD, PaymentMethodType.PAY123),
                jioPriority = listOf(PaymentMethodType.PAY123, PaymentMethodType.USSD),
                otherPriority = listOf(PaymentMethodType.USSD, PaymentMethodType.PAY123),
            )

        /** Alias for [DEFAULT] — retained for backward compatibility. */
        fun default() = RoutingPriorityConfig(
            airtelPriority = listOf(PaymentMethodType.USSD, PaymentMethodType.PAY123),
            viPriority = listOf(PaymentMethodType.USSD, PaymentMethodType.PAY123),
            bsnlPriority = listOf(PaymentMethodType.USSD, PaymentMethodType.PAY123),
            jioPriority = listOf(PaymentMethodType.PAY123, PaymentMethodType.USSD),
            otherPriority = listOf(PaymentMethodType.USSD, PaymentMethodType.PAY123),
        )

        /**
         * Returns the priority list for a specific [OperatorType].
         */
        fun RoutingPriorityConfig.priorityFor(operator: OperatorType): List<PaymentMethodType> =
            when (operator) {
                OperatorType.AIRTEL -> airtelPriority
                OperatorType.VI -> viPriority
                OperatorType.BSNL -> bsnlPriority
                OperatorType.JIO -> jioPriority
                OperatorType.OTHER -> otherPriority
            }
    }
}
