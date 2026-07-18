package com.offlinepay.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Raw palette — design tokens (Section 10.3)
// ---------------------------------------------------------------------------

/** Deep Indigo — trust, reliability */
val BrandPrimary = Color(0xFF3D2DB5)
val BrandPrimaryDark = Color(0xFF6B5CE7)        // lighter variant for dark theme
val BrandPrimaryContainer = Color(0xFFDDD8FF)
val BrandPrimaryContainerDark = Color(0xFF1E1680)
val BrandOnPrimary = Color(0xFFFFFFFF)
val BrandOnPrimaryDark = Color(0xFFFFFFFF)
val BrandOnPrimaryContainer = Color(0xFF1A0B6B)
val BrandOnPrimaryContainerDark = Color(0xFFDDD8FF)

/** Amber accent — energy, action */
val BrandSecondary = Color(0xFFF59E0B)
val BrandSecondaryDark = Color(0xFFFBBF24)
val BrandSecondaryContainer = Color(0xFFFEF3C7)
val BrandSecondaryContainerDark = Color(0xFF78350F)
val BrandOnSecondary = Color(0xFF1C1C1C)
val BrandOnSecondaryDark = Color(0xFF1C1C1C)

// Semantic status colours
val SuccessGreen = Color(0xFF16A34A)
val WarningAmber = Color(0xFFD97706)
val ErrorRed = Color(0xFFDC2626)
val InfoBlue = Color(0xFF2563EB)
val PendingOrange = Color(0xFFEA580C)
val UnknownGrey = Color(0xFF6B7280)

// Status containers (light backgrounds for status cards)
val SuccessContainer = Color(0xFFDCFCE7)
val WarningContainer = Color(0xFFFEF3C7)
val ErrorContainer = Color(0xFFFEE2E2)
val InfoContainer = Color(0xFFDBEAFE)

// Dark-mode status containers
val SuccessContainerDark = Color(0xFF14532D)
val WarningContainerDark = Color(0xFF78350F)
val ErrorContainerDark = Color(0xFF7F1D1D)
val InfoContainerDark = Color(0xFF1E3A8A)

// On-status colours (text/icons rendered on containers)
val OnSuccessContainer = Color(0xFF14532D)
val OnWarningContainer = Color(0xFF78350F)
val OnErrorContainer = Color(0xFF7F1D1D)
val OnInfoContainer = Color(0xFF1E3A8A)
val OnSuccessContainerDark = Color(0xFFBBF7D0)
val OnWarningContainerDark = Color(0xFFFDE68A)
val OnErrorContainerDark = Color(0xFFFECACA)
val OnInfoContainerDark = Color(0xFFBFDBFE)

// Surface palette — light
val SurfaceBackgroundLight = Color(0xFFF8FAFC)
val SurfaceCardLight = Color(0xFFFFFFFF)
val SurfaceElevatedLight = Color(0xFFF1F5F9)
val SurfaceVariantLight = Color(0xFFE2E8F0)
val OnSurfaceLight = Color(0xFF0F172A)
val OnSurfaceVariantLight = Color(0xFF475569)
val OutlineLight = Color(0xFFCBD5E1)
val OutlineVariantLight = Color(0xFFE2E8F0)

// Surface palette — dark
val SurfaceBackgroundDark = Color(0xFF0F172A)
val SurfaceCardDark = Color(0xFF1E293B)
val SurfaceElevatedDark = Color(0xFF334155)
val SurfaceVariantDark = Color(0xFF334155)
val OnSurfaceDark = Color(0xFFF1F5F9)
val OnSurfaceVariantDark = Color(0xFF94A3B8)
val OutlineDark = Color(0xFF475569)
val OutlineVariantDark = Color(0xFF334155)

// High-contrast status overrides (WCAG 2.1 AAA, ≥7:1)
val HcSuccessGreen = Color(0xFF14532D)
val HcWarningAmber = Color(0xFF92400E)
val HcErrorRed = Color(0xFF7F1D1D)
val HcInfoBlue = Color(0xFF1E3A8A)
val HcPendingOrange = Color(0xFF7C2D12)
val HcUnknownGrey = Color(0xFF1E293B)
val HcBrandPrimary = Color(0xFF1A0B6B)

// ---------------------------------------------------------------------------
// OfflinePayColors — semantic token set consumed by Compose UI
// ---------------------------------------------------------------------------

/**
 * Semantic color tokens for OfflinePay (Section 10.3).
 *
 * Compose consumers read these tokens via [LocalOfflinePayColors]:
 * ```kotlin
 * val colors = LocalOfflinePayColors.current
 * Text(color = colors.successGreen)
 * ```
 */
@Immutable
data class OfflinePayColors(
    // Brand
    val brandPrimary: Color,
    val brandOnPrimary: Color,
    val brandPrimaryContainer: Color,
    val brandOnPrimaryContainer: Color,
    val brandSecondary: Color,
    val brandOnSecondary: Color,
    val brandSecondaryContainer: Color,

    // Semantic status
    val successGreen: Color,
    val warningAmber: Color,
    val errorRed: Color,
    val infoBlue: Color,
    val pendingOrange: Color,
    val unknownGrey: Color,

    // Status containers + on-container
    val successContainer: Color,
    val warningContainer: Color,
    val errorContainer: Color,
    val infoContainer: Color,
    val onSuccessContainer: Color,
    val onWarningContainer: Color,
    val onErrorContainer: Color,
    val onInfoContainer: Color,

    // Surface variants
    val surfaceBackground: Color,
    val surfaceCard: Color,
    val surfaceElevated: Color,
    val surfaceVariant: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,

    /** `true` for the dark scheme, `false` for light and high-contrast. */
    val isDark: Boolean,

    /** `true` when the high-contrast accessibility scheme is active. */
    val isHighContrast: Boolean,
)

// ---------------------------------------------------------------------------
// Light scheme
// ---------------------------------------------------------------------------

/**
 * Light color scheme — WCAG 2.1 AA (4.5:1 text, 3:1 large text).
 */
val LightOfflinePayColors = OfflinePayColors(
    brandPrimary = BrandPrimary,
    brandOnPrimary = BrandOnPrimary,
    brandPrimaryContainer = BrandPrimaryContainer,
    brandOnPrimaryContainer = BrandOnPrimaryContainer,
    brandSecondary = BrandSecondary,
    brandOnSecondary = BrandOnSecondary,
    brandSecondaryContainer = BrandSecondaryContainer,

    successGreen = SuccessGreen,
    warningAmber = WarningAmber,
    errorRed = ErrorRed,
    infoBlue = InfoBlue,
    pendingOrange = PendingOrange,
    unknownGrey = UnknownGrey,

    successContainer = SuccessContainer,
    warningContainer = WarningContainer,
    errorContainer = ErrorContainer,
    infoContainer = InfoContainer,
    onSuccessContainer = OnSuccessContainer,
    onWarningContainer = OnWarningContainer,
    onErrorContainer = OnErrorContainer,
    onInfoContainer = OnInfoContainer,

    surfaceBackground = SurfaceBackgroundLight,
    surfaceCard = SurfaceCardLight,
    surfaceElevated = SurfaceElevatedLight,
    surfaceVariant = SurfaceVariantLight,
    onSurface = OnSurfaceLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,

    isDark = false,
    isHighContrast = false,
)

// ---------------------------------------------------------------------------
// Dark scheme
// ---------------------------------------------------------------------------

/**
 * Dark color scheme — WCAG 2.1 AA with elevated surfaces and lighter status tones.
 */
val DarkOfflinePayColors = OfflinePayColors(
    brandPrimary = BrandPrimaryDark,
    brandOnPrimary = BrandOnPrimaryDark,
    brandPrimaryContainer = BrandPrimaryContainerDark,
    brandOnPrimaryContainer = BrandOnPrimaryContainerDark,
    brandSecondary = BrandSecondaryDark,
    brandOnSecondary = BrandOnSecondaryDark,
    brandSecondaryContainer = BrandSecondaryContainerDark,

    successGreen = Color(0xFF4ADE80),     // lighter for dark bg contrast
    warningAmber = Color(0xFFFBBF24),
    errorRed = Color(0xFFF87171),
    infoBlue = Color(0xFF60A5FA),
    pendingOrange = Color(0xFFFB923C),
    unknownGrey = Color(0xFF94A3B8),

    successContainer = SuccessContainerDark,
    warningContainer = WarningContainerDark,
    errorContainer = ErrorContainerDark,
    infoContainer = InfoContainerDark,
    onSuccessContainer = OnSuccessContainerDark,
    onWarningContainer = OnWarningContainerDark,
    onErrorContainer = OnErrorContainerDark,
    onInfoContainer = OnInfoContainerDark,

    surfaceBackground = SurfaceBackgroundDark,
    surfaceCard = SurfaceCardDark,
    surfaceElevated = SurfaceElevatedDark,
    surfaceVariant = SurfaceVariantDark,
    onSurface = OnSurfaceDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,

    isDark = true,
    isHighContrast = false,
)

// ---------------------------------------------------------------------------
// High-contrast scheme (WCAG 2.1 AAA — ≥7:1 for all body text)
// ---------------------------------------------------------------------------

/**
 * High-contrast color scheme for users with Android's high-contrast text
 * accessibility setting enabled (Req 14.7).  Uses deeply saturated, dark-on-white
 * tokens that meet the WCAG 2.1 AAA 7:1 ratio for all body-size text.
 */
val HighContrastOfflinePayColors = OfflinePayColors(
    brandPrimary = HcBrandPrimary,
    brandOnPrimary = Color(0xFFFFFFFF),
    brandPrimaryContainer = Color(0xFF1A0B6B),
    brandOnPrimaryContainer = Color(0xFFFFFFFF),
    brandSecondary = Color(0xFF92400E),
    brandOnSecondary = Color(0xFFFFFFFF),
    brandSecondaryContainer = Color(0xFF78350F),

    successGreen = HcSuccessGreen,
    warningAmber = HcWarningAmber,
    errorRed = HcErrorRed,
    infoBlue = HcInfoBlue,
    pendingOrange = HcPendingOrange,
    unknownGrey = HcUnknownGrey,

    successContainer = Color(0xFFDCFCE7),
    warningContainer = Color(0xFFFEF3C7),
    errorContainer = Color(0xFFFEE2E2),
    infoContainer = Color(0xFFDBEAFE),
    onSuccessContainer = HcSuccessGreen,
    onWarningContainer = HcWarningAmber,
    onErrorContainer = HcErrorRed,
    onInfoContainer = HcInfoBlue,

    surfaceBackground = Color(0xFFFFFFFF),
    surfaceCard = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurface = Color(0xFF000000),
    onSurfaceVariant = Color(0xFF0F172A),
    outline = Color(0xFF000000),
    outlineVariant = Color(0xFF334155),

    isDark = false,
    isHighContrast = true,
)

// ---------------------------------------------------------------------------
// Material 3 ColorScheme bridges
// ---------------------------------------------------------------------------

/**
 * Maps [OfflinePayColors] tokens onto a Material 3 [ColorScheme] so that
 * [OfflinePayTheme] can pass it to [MaterialTheme].
 */
fun OfflinePayColors.toMaterial3ColorScheme(): ColorScheme = if (isDark) {
    darkColorScheme(
        primary = brandPrimary,
        onPrimary = brandOnPrimary,
        primaryContainer = brandPrimaryContainer,
        onPrimaryContainer = brandOnPrimaryContainer,
        secondary = brandSecondary,
        onSecondary = brandOnSecondary,
        secondaryContainer = brandSecondaryContainer,
        error = errorRed,
        background = surfaceBackground,
        surface = surfaceCard,
        surfaceVariant = surfaceVariant,
        onSurface = onSurface,
        onSurfaceVariant = onSurfaceVariant,
        outline = outline,
        outlineVariant = outlineVariant,
    )
} else {
    lightColorScheme(
        primary = brandPrimary,
        onPrimary = brandOnPrimary,
        primaryContainer = brandPrimaryContainer,
        onPrimaryContainer = brandOnPrimaryContainer,
        secondary = brandSecondary,
        onSecondary = brandOnSecondary,
        secondaryContainer = brandSecondaryContainer,
        error = errorRed,
        background = surfaceBackground,
        surface = surfaceCard,
        surfaceVariant = surfaceVariant,
        onSurface = onSurface,
        onSurfaceVariant = onSurfaceVariant,
        outline = outline,
        outlineVariant = outlineVariant,
    )
}

// ---------------------------------------------------------------------------
// CompositionLocal — provides OfflinePayColors down the Compose tree
// ---------------------------------------------------------------------------

/**
 * CompositionLocal that carries the current [OfflinePayColors] instance.
 * Access via `LocalOfflinePayColors.current` within any Composable under
 * [OfflinePayTheme].
 */
val LocalOfflinePayColors = staticCompositionLocalOf { LightOfflinePayColors }
