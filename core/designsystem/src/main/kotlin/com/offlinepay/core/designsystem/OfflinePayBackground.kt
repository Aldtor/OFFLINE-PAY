package com.offlinepay.core.designsystem

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------------------
// OfflinePayBackground — the ambient "aurora" backdrop for every screen.
//
// A vertical base gradient overlaid with three soft radial colour blobs. The
// blobs use radial gradients (soft falloff on ALL API levels), and on API 31+
// an extra RenderEffect blur is applied for a genuinely dreamy frosted depth.
// Glass surfaces placed on top let this colour bleed through, selling the
// glassmorphism effect without any third-party blur library.
// ---------------------------------------------------------------------------

/**
 * Wraps screen [content] in the branded aurora background.
 *
 * Replace a screen's outermost container with this and give the inner
 * `Scaffold` a transparent container colour so the aurora shows through:
 * ```kotlin
 * OfflinePayBackground {
 *     Scaffold(containerColor = Color.Transparent) { ... }
 * }
 * ```
 *
 * The [content] lambda runs in a [BoxScope] filling the whole screen.
 *
 * @param modifier Modifier applied to the root background box.
 * @param content  Screen content drawn on top of the aurora.
 */
@Composable
fun OfflinePayBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val tokens = LocalGlassTokens.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                // Base vertical gradient.
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(tokens.backgroundTop, tokens.backgroundBottom),
                    ),
                )
            },
    ) {
        // Aurora blob layer — blurred on API 31+, softly radial everywhere.
        val blobModifier = Modifier
            .fillMaxSize()
            .then(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Modifier.blur(64.dp)
                } else {
                    Modifier
                },
            )
            .drawBehind {
                // Top-left indigo glow.
                drawCircleBlob(
                    color = tokens.auroraPrimary,
                    center = Offset(x = size.width * 0.12f, y = size.height * 0.08f),
                    radius = size.minDimension * 0.85f,
                )
                // Bottom-right amber glow.
                drawCircleBlob(
                    color = tokens.auroraSecondary,
                    center = Offset(x = size.width * 0.95f, y = size.height * 0.72f),
                    radius = size.minDimension * 0.95f,
                )
                // Mid-right cyan accent.
                drawCircleBlob(
                    color = tokens.auroraTertiary,
                    center = Offset(x = size.width * 1.05f, y = size.height * 0.30f),
                    radius = size.minDimension * 0.70f,
                )
            }

        Box(modifier = blobModifier)

        // Foreground content.
        content()
    }
}

/**
 * Draws a single soft radial "blob" — a colour that fades to transparent at
 * [radius]. Used to compose the aurora without any hard circle edges.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCircleBlob(
    color: androidx.compose.ui.graphics.Color,
    center: Offset,
    radius: Float,
) {
    if (color.alpha == 0f) return
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(color, androidx.compose.ui.graphics.Color.Transparent),
            center = center,
            radius = radius,
        ),
        topLeft = Offset.Zero,
        size = Size(size.width, size.height),
    )
}
