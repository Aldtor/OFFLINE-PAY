package com.offlinepay.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Full-screen QR scanner targeting overlay.
 *
 * Renders a semi-transparent dark scrim over the camera preview with a transparent
 * square cutout showing the scan area, plus L-shaped white corner brackets at each
 * corner of the reticle. The cutout is 70% of the minimum screen dimension.
 *
 * Implementation notes:
 * - Uses [BlendMode.Clear] on a hardware-accelerated Canvas to punch the transparent
 *   square through the dark scrim — requires `graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)`
 *   on the parent, or we draw using two-pass approach here.
 * - The scrim color is `Color(0x99000000)` (~60% black) per design Section 10.3.
 * - Accessibility: the overlay has `contentDescription = "QR code scanning area"` per Req 14.5.
 *
 * Design reference: Section 3.2, 10.3
 * Requirements: Req 14.5
 *
 * ### Usage
 * ```kotlin
 * Box(Modifier.fillMaxSize()) {
 *     CameraPreview(Modifier.fillMaxSize())
 *     ScannerOverlay()
 * }
 * ```
 *
 * @param modifier         Modifier applied to the canvas.
 * @param scrubColor       Scrim color. Defaults to 60% black per design.
 * @param cornerColor      Color of the L-shaped corner bracket guides. Defaults to white.
 * @param cornerLength     Length of each arm of an L-bracket corner guide.
 * @param cornerStrokeWidth  Stroke width of the corner bracket guides.
 */
@Composable
fun ScannerOverlay(
    modifier: Modifier = Modifier,
    scrubColor: Color = Color(0x99000000),
    cornerColor: Color = Color.White,
    cornerLength: Dp = 32.dp,
    cornerStrokeWidth: Dp = 4.dp,
) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .semantics {
                contentDescription = "QR code scanning area"
            },
    ) {
        val cutoutFraction = 0.70f
        val reticleSize = minOf(size.width, size.height) * cutoutFraction
        val reticleLeft = (size.width - reticleSize) / 2f
        val reticleTop = (size.height - reticleSize) / 2f
        val reticleRect = Rect(
            left = reticleLeft,
            top = reticleTop,
            right = reticleLeft + reticleSize,
            bottom = reticleTop + reticleSize,
        )

        // Draw the scrim over the full canvas, then clear the reticle square.
        // Using Path + BlendMode.Clear requires the composable tree to enable
        // graphicsLayer offscreen compositing; calling drawRect + BlendMode.Clear
        // achieves the same effect when the Canvas has an offscreen compositing strategy.
        // In practice, wrapping in a graphicsLayer with CompositingStrategy.Offscreen
        // at the caller side ensures this works on all hardware. We draw scrim first,
        // then punch out the hole.
        drawRect(color = scrubColor)

        // Punch the transparent cutout hole using BlendMode.Clear.
        drawRect(
            color = Color.Transparent,
            topLeft = Offset(reticleRect.left, reticleRect.top),
            size = Size(reticleSize, reticleSize),
            blendMode = BlendMode.Clear,
        )

        // Draw white corner brackets at each of the four corners.
        val cl = cornerLength.toPx()
        val csw = cornerStrokeWidth.toPx()

        drawCornerBrackets(
            reticleRect = reticleRect,
            cornerLength = cl,
            strokeWidth = csw,
            color = cornerColor,
        )
    }
}

/**
 * Draws L-shaped corner brackets at all four corners of [reticleRect].
 *
 * Each bracket consists of two line segments (horizontal + vertical) of [cornerLength]
 * starting from the outermost point of a corner and extending inward along each edge.
 */
private fun DrawScope.drawCornerBrackets(
    reticleRect: Rect,
    cornerLength: Float,
    strokeWidth: Float,
    color: Color,
) {
    val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Square)

    // ── Top-left corner ──────────────────────────────────────────────────────
    // Horizontal arm: left edge → inward
    drawLine(
        color = color,
        start = Offset(reticleRect.left, reticleRect.top),
        end = Offset(reticleRect.left + cornerLength, reticleRect.top),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Square,
    )
    // Vertical arm: top edge → downward
    drawLine(
        color = color,
        start = Offset(reticleRect.left, reticleRect.top),
        end = Offset(reticleRect.left, reticleRect.top + cornerLength),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Square,
    )

    // ── Top-right corner ─────────────────────────────────────────────────────
    drawLine(
        color = color,
        start = Offset(reticleRect.right, reticleRect.top),
        end = Offset(reticleRect.right - cornerLength, reticleRect.top),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Square,
    )
    drawLine(
        color = color,
        start = Offset(reticleRect.right, reticleRect.top),
        end = Offset(reticleRect.right, reticleRect.top + cornerLength),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Square,
    )

    // ── Bottom-left corner ───────────────────────────────────────────────────
    drawLine(
        color = color,
        start = Offset(reticleRect.left, reticleRect.bottom),
        end = Offset(reticleRect.left + cornerLength, reticleRect.bottom),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Square,
    )
    drawLine(
        color = color,
        start = Offset(reticleRect.left, reticleRect.bottom),
        end = Offset(reticleRect.left, reticleRect.bottom - cornerLength),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Square,
    )

    // ── Bottom-right corner ──────────────────────────────────────────────────
    drawLine(
        color = color,
        start = Offset(reticleRect.right, reticleRect.bottom),
        end = Offset(reticleRect.right - cornerLength, reticleRect.bottom),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Square,
    )
    drawLine(
        color = color,
        start = Offset(reticleRect.right, reticleRect.bottom),
        end = Offset(reticleRect.right, reticleRect.bottom - cornerLength),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Square,
    )
}
