package com.offlinepay.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------------------
// Glass surface primitives — the building blocks of the frosted-glass look.
// ---------------------------------------------------------------------------

/** Default corner radius for glass cards (continuous, iOS-like softness). */
val GlassCornerRadius: Dp = 26.dp

/**
 * Applies the frosted-glass treatment to any layout node:
 *
 * 1. a soft ambient drop shadow (colour-tinted, low alpha),
 * 2. a clip to [shape],
 * 3. a translucent fill (from [tokens]),
 * 4. a top-to-bottom sheen so the top rim catches light, and
 * 5. a hairline border.
 *
 * Because the fill is a semi-transparent colour, the ambient aurora background
 * shows through — reading as real glass. On API 31+ the caller may additionally
 * blur the *background* layer for a true backdrop-frost effect; this modifier
 * itself needs no blur and is safe on every supported API level.
 *
 * @param tokens    Glass token set — usually `LocalGlassTokens.current`.
 * @param shape     Surface shape. Defaults to a [GlassCornerRadius] rounded rect.
 * @param elevated  When `true`, uses the stronger [GlassTokens.fillElevated] and
 *                  a deeper shadow — for bars, sheets and hero surfaces.
 */
@Composable
fun Modifier.glassSurface(
    tokens: GlassTokens,
    shape: Shape = RoundedCornerShape(GlassCornerRadius),
    elevated: Boolean = false,
): Modifier {
    val fill = if (elevated) tokens.fillElevated else tokens.fill
    val shadowElevation = if (elevated) 18.dp else 10.dp

    return this
        .shadow(
            elevation = shadowElevation,
            shape = shape,
            clip = false,
            ambientColor = tokens.shadow,
            spotColor = tokens.shadow,
        )
        .clip(shape)
        .background(color = fill, shape = shape)
        // Top sheen: a bright highlight fading out over the upper third.
        .drawWithContent {
            drawContent()
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(tokens.highlight, Color.Transparent),
                    startY = 0f,
                    endY = size.height * 0.55f,
                ),
                topLeft = Offset.Zero,
                size = size.copy(height = size.height * 0.55f),
            )
        }
        .border(BorderStroke(1.dp, tokens.border), shape)
}

/**
 * A frosted-glass card — the primary premium surface for OfflinePay content.
 *
 * Drop-in replacement for a Material 3 `Card`: place content inside and pad it
 * as usual. Renders as a translucent panel with a hairline border, top sheen
 * and soft shadow over the ambient [OfflinePayBackground].
 *
 * ### Usage
 * ```kotlin
 * GlassCard(onClick = { open() }, modifier = Modifier.fillMaxWidth()) {
 *     Column(Modifier.padding(OfflinePaySpacing.M)) { Text("Payment") }
 * }
 * ```
 *
 * @param modifier  Modifier applied to the card container.
 * @param shape     Card shape. Defaults to [GlassCornerRadius].
 * @param elevated  Use the stronger elevated glass treatment.
 * @param onClick   Optional click handler; when non-null the card is clickable.
 * @param content   Card content.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(GlassCornerRadius),
    elevated: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val tokens = LocalGlassTokens.current
    val base = modifier.glassSurface(tokens = tokens, shape = shape, elevated = elevated)
    val surface = if (onClick != null) base.clickable(onClick = onClick) else base
    Box(modifier = surface) {
        content()
    }
}
