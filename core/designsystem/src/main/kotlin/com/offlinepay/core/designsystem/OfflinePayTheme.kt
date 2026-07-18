package com.offlinepay.core.designsystem

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------------------
// OfflinePay shape tokens — Req 14.9
// ---------------------------------------------------------------------------

/**
 * OfflinePay shape scale.
 *
 * Per Req 14.9:
 *   - Cards  → [medium] = 20dp rounded corners
 *   - Buttons → [small]  = 18dp rounded corners
 *
 * These are surfaced to Material 3 via [MaterialTheme.shapes] so all M3
 * Card and Button composables automatically pick up the correct radius.
 */
val OfflinePayShapes = Shapes(
    // Chips / small controls — soft pill-ish radius.
    extraSmall = RoundedCornerShape(14.dp),
    // Buttons — 20dp continuous-feel radius (premium glass language).
    small = RoundedCornerShape(20.dp),
    // Cards — 26dp rounded corners to match the glass surface radius.
    medium = RoundedCornerShape(26.dp),
    // Dialogs / large surfaces — 32dp for an iOS-style softness.
    large = RoundedCornerShape(32.dp),
    // Extra-large surfaces (bottom sheets) — 36dp.
    extraLarge = RoundedCornerShape(36.dp),
)

// ---------------------------------------------------------------------------
// OfflinePayTheme
// ---------------------------------------------------------------------------

/**
 * Root theme composable for OfflinePay.
 *
 * Wraps [MaterialTheme] with the OfflinePay-specific color scheme, typography,
 * and shape scale, and provides [LocalOfflinePayColors] down the composition
 * tree for access to semantic color tokens that extend beyond M3's built-in
 * color roles.
 *
 * ### Color scheme selection (priority order):
 * 1. **High-contrast** — applied when
 *    [AccessibilityManager.isHighContrastTextEnabled] returns `true`.
 *    Tokens meet WCAG 2.1 AAA (≥ 7:1) for all body-size text (Req 14.7).
 * 2. **Dark** — applied when [darkTheme] is `true`.
 * 3. **Light** — the default.
 *
 * ### Shape tokens (Req 14.9):
 * - Cards  → `MaterialTheme.shapes.medium` = 20dp rounded corners
 * - Buttons → `MaterialTheme.shapes.small`  = 18dp rounded corners
 *
 * ### Usage:
 * ```kotlin
 * OfflinePayTheme(darkTheme = isSystemInDarkTheme()) {
 *     // All Composables in this tree inherit the brand theme.
 *     // Semantic color access:
 *     val colors = LocalOfflinePayColors.current
 *     Text(color = colors.successGreen, text = "Paid")
 * }
 * ```
 *
 * @param darkTheme  Whether the dark color scheme should be used.
 *                   Typically driven by [androidx.compose.foundation.isSystemInDarkTheme].
 * @param content    Composable content rendered inside the theme.
 */
@Composable
fun OfflinePayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current

    // Resolve whether high-contrast text mode is active (Req 14.7).
    // AccessibilityManager.isHighContrastTextEnabled() requires API 31+ (Android S).
    // We access it via reflection to avoid unresolved reference issues on older compile targets.
    val isHighContrast = remember(context) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                val method = am?.javaClass?.getMethod("isHighContrastTextEnabled")
                method?.invoke(am) as? Boolean ?: false
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    // High-contrast takes precedence over dark/light (Req 14.7).
    val offlinePayColors = when {
        isHighContrast -> HighContrastOfflinePayColors
        darkTheme      -> DarkOfflinePayColors
        else           -> LightOfflinePayColors
    }

    val glassTokens = glassTokensFor(
        isDark = offlinePayColors.isDark,
        isHighContrast = offlinePayColors.isHighContrast,
    )

    CompositionLocalProvider(
        LocalOfflinePayColors provides offlinePayColors,
        LocalGlassTokens provides glassTokens,
    ) {
        MaterialTheme(
            colorScheme = offlinePayColors.toMaterial3ColorScheme(),
            typography  = OfflinePayTypography,
            shapes      = OfflinePayShapes,
            content     = content,
        )
    }
}
