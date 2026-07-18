package com.offlinepay.core.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * An infinite shimmer animation brush that sweeps a gradient highlight from left to right,
 * producing a skeleton loading effect.
 *
 * The composable measures its own width via [onGloballyPositioned] and drives a
 * `rememberInfiniteTransition` that moves [xShimmer] from 0f → 1f.  The shimmer
 * gradient is a diagonal sweep from (0,0) to (fullWidth * xShimmer + gap, same),
 * where `gap` prevents the gradient from stalling at the edges.
 *
 * The three shimmer colors produce a neutral highlight pulse:
 * - `0xFFE0E0E0` — base grey
 * - `0xFFF5F5F5` — bright highlight center
 * - `0xFFE0E0E0` — base grey (trailing edge)
 *
 * Design reference: Section 3.2, 10.3 — SkeletonCard / ShimmerEffect
 * Requirements: Req 15.6, Req 15.7
 *
 * ### Usage
 * ```kotlin
 * Box(
 *     modifier = Modifier
 *         .fillMaxWidth()
 *         .height(80.dp)
 *         .clip(RoundedCornerShape(20.dp))
 * ) {
 *     ShimmerEffect(modifier = Modifier.fillMaxSize())
 * }
 * ```
 *
 * @param modifier Modifier applied to the shimmer [Box].
 */
@Composable
fun ShimmerEffect(modifier: Modifier = Modifier) {
    // Track the rendered width so the gradient sweep covers the full component.
    var fullWidth by remember { mutableFloatStateOf(0f) }

    // Animate xShimmer from 0f → 1f in a continuous loop.
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val xShimmer by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
        ),
        label = "xShimmer",
    )

    // A small gap constant (equal to half the width) keeps the highlight moving
    // smoothly without snapping back at the end of the width.
    val gap = fullWidth * 0.5f
    val sweepX = fullWidth * xShimmer + gap

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFFE0E0E0),
            Color(0xFFF5F5F5),
            Color(0xFFE0E0E0),
        ),
        start = Offset(0f, 0f),
        end = Offset(sweepX, sweepX),
    )

    Box(
        modifier = modifier
            .background(shimmerBrush)
            .onGloballyPositioned { coords ->
                fullWidth = coords.size.width.toFloat()
            },
    )
}

/**
 * A rounded-rectangle loading placeholder card filled with an animated [ShimmerEffect].
 *
 * Use this composable in `LazyColumn` or grid layouts to represent loading content
 * while real data is being fetched.  The shape (20dp corner radius) mirrors
 * [PaymentCard] so skeleton layouts are visually consistent with their loaded state.
 *
 * Design reference: Section 3.2, 10.3 — SkeletonCard
 * Requirements: Req 15.6, Req 15.7
 *
 * ### Usage
 * ```kotlin
 * // Show 3 skeleton cards while loading
 * repeat(3) {
 *     SkeletonCard(
 *         modifier = Modifier
 *             .fillMaxWidth()
 *             .height(96.dp)
 *             .padding(horizontal = 16.dp, vertical = 8.dp),
 *     )
 * }
 * ```
 *
 * @param modifier Modifier applied to the outer rounded-rectangle container.
 *                 Callers should specify width and height via the modifier.
 */
@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .fillMaxWidth()
            .height(80.dp), // default height; callers override via their own modifier
    ) {
        ShimmerEffect(modifier = Modifier.fillMaxSize())
    }
}
