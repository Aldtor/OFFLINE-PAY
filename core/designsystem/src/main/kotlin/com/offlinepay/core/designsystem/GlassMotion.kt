package com.offlinepay.core.designsystem

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

// ---------------------------------------------------------------------------
// GlassMotion — springy, iOS-flavoured motion helpers.
//
// Apple UIs feel alive because interactive elements respond with a soft spring
// rather than a linear tween. These helpers give buttons and cards a subtle
// "press-in" scale so the whole app feels tactile and premium.
// ---------------------------------------------------------------------------

/** Gentle, low-bounce spring — the default for most glass transitions. */
fun <T> gentleSpring() = spring<T>(
    dampingRatio = 0.85f,
    stiffness = Spring.StiffnessMediumLow,
)

/** Bouncier spring for celebratory moments (payment success, confirmations). */
fun <T> bouncySpring() = spring<T>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessLow,
)

/**
 * Adds an iOS-style "press-in" response: the element springs down to
 * [pressedScale] while held and springs back on release.
 *
 * Attach the same [interactionSource] to the node's `clickable`/`combinedClickable`
 * so presses are detected:
 * ```kotlin
 * val interaction = remember { MutableInteractionSource() }
 * Box(
 *     Modifier
 *         .pressScale(interaction)
 *         .clickable(interactionSource = interaction, indication = null) { ... }
 * )
 * ```
 *
 * @param interactionSource Source whose press state drives the scale.
 * @param pressedScale      Scale factor while pressed (default 0.96).
 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.96f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = gentleSpring(),
        label = "pressScale",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Convenience: remembers a [MutableInteractionSource] for press-driven motion.
 */
@Composable
fun rememberPressInteraction(): MutableInteractionSource =
    remember { MutableInteractionSource() }
