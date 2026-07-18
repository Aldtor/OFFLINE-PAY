package com.offlinepay.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Glassmorphism design tokens — premium "frosted glass" surface system.
//
// These tokens power the Apple-style glass aesthetic: translucent surfaces,
// hairline borders, a subtle top sheen, and an ambient aurora background made
// of soft radial colour "blobs".  All values are plain [Color]s with alpha —
// they render identically on every supported API level (minSdk 26); the real
// RenderEffect blur (API 31+) is layered on top purely as an enhancement.
// ---------------------------------------------------------------------------

/**
 * Semantic token set for the glass / aurora visual language.
 *
 * Consumed via [LocalGlassTokens] inside any Composable under [OfflinePayTheme]:
 * ```kotlin
 * val glass = LocalGlassTokens.current
 * Box(Modifier.glassSurface(glass))
 * ```
 */
@Immutable
data class GlassTokens(
    /** Primary translucent fill for standard glass surfaces (cards, rows). */
    val fill: Color,
    /** Stronger translucent fill for raised / emphasised glass (bars, sheets). */
    val fillElevated: Color,
    /** Faint fill used for subtle, low-emphasis glass chips. */
    val fillSubtle: Color,
    /** Hairline border colour that gives the glass its crisp edge. */
    val border: Color,
    /** Bright top-edge sheen simulating a light catching the glass rim. */
    val highlight: Color,
    /** Soft ambient shadow colour cast beneath glass surfaces. */
    val shadow: Color,
    /** Top → bottom stops for the ambient screen background gradient. */
    val backgroundTop: Color,
    val backgroundBottom: Color,
    /** Decorative aurora blob colours (already alpha-tinted for radial fade). */
    val auroraPrimary: Color,
    val auroraSecondary: Color,
    val auroraTertiary: Color,
    /** `true` for the dark scheme. */
    val isDark: Boolean,
)

// ---------------------------------------------------------------------------
// Light glass — bright frosted white over a soft indigo/amber aurora.
// ---------------------------------------------------------------------------

val LightGlassTokens = GlassTokens(
    fill = Color(0xFFFFFFFF).copy(alpha = 0.58f),
    fillElevated = Color(0xFFFFFFFF).copy(alpha = 0.72f),
    fillSubtle = Color(0xFFFFFFFF).copy(alpha = 0.38f),
    border = Color(0xFFFFFFFF).copy(alpha = 0.70f),
    highlight = Color(0xFFFFFFFF).copy(alpha = 0.85f),
    shadow = Color(0xFF3D2DB5).copy(alpha = 0.14f),
    backgroundTop = Color(0xFFF3F1FF),      // pale indigo
    backgroundBottom = Color(0xFFFDF6EC),   // pale amber
    auroraPrimary = Color(0xFF6B5CE7).copy(alpha = 0.38f),   // indigo glow
    auroraSecondary = Color(0xFFF59E0B).copy(alpha = 0.26f), // amber glow
    auroraTertiary = Color(0xFF22D3EE).copy(alpha = 0.22f),  // cyan glow
    isDark = false,
)

// ---------------------------------------------------------------------------
// Dark glass — smoky translucent panels over a deep indigo aurora.
// ---------------------------------------------------------------------------

val DarkGlassTokens = GlassTokens(
    fill = Color(0xFFFFFFFF).copy(alpha = 0.08f),
    fillElevated = Color(0xFFFFFFFF).copy(alpha = 0.14f),
    fillSubtle = Color(0xFFFFFFFF).copy(alpha = 0.05f),
    border = Color(0xFFFFFFFF).copy(alpha = 0.16f),
    highlight = Color(0xFFFFFFFF).copy(alpha = 0.24f),
    shadow = Color(0xFF000000).copy(alpha = 0.45f),
    backgroundTop = Color(0xFF0B1020),      // near-black indigo
    backgroundBottom = Color(0xFF160E2E),   // deep violet
    auroraPrimary = Color(0xFF6B5CE7).copy(alpha = 0.40f),
    auroraSecondary = Color(0xFFF59E0B).copy(alpha = 0.20f),
    auroraTertiary = Color(0xFF22D3EE).copy(alpha = 0.20f),
    isDark = true,
)

// ---------------------------------------------------------------------------
// High-contrast glass — opaque, high-legibility fallback that keeps the shape
// language but drops translucency so WCAG AAA contrast is preserved (Req 14.7).
// ---------------------------------------------------------------------------

val HighContrastGlassTokens = GlassTokens(
    fill = Color(0xFFFFFFFF).copy(alpha = 0.96f),
    fillElevated = Color(0xFFFFFFFF),
    fillSubtle = Color(0xFFFFFFFF).copy(alpha = 0.90f),
    border = Color(0xFF000000).copy(alpha = 0.55f),
    highlight = Color(0xFFFFFFFF),
    shadow = Color(0xFF000000).copy(alpha = 0.30f),
    backgroundTop = Color(0xFFFFFFFF),
    backgroundBottom = Color(0xFFF1F5F9),
    auroraPrimary = Color.Transparent,
    auroraSecondary = Color.Transparent,
    auroraTertiary = Color.Transparent,
    isDark = false,
)

/**
 * Resolves the glass token set for the active scheme, mirroring the priority
 * order used by [OfflinePayTheme]: high-contrast → dark → light.
 */
fun glassTokensFor(isDark: Boolean, isHighContrast: Boolean): GlassTokens = when {
    isHighContrast -> HighContrastGlassTokens
    isDark -> DarkGlassTokens
    else -> LightGlassTokens
}

/**
 * CompositionLocal carrying the current [GlassTokens]. Provided by
 * [OfflinePayTheme]; read via `LocalGlassTokens.current`.
 */
val LocalGlassTokens = staticCompositionLocalOf { LightGlassTokens }
