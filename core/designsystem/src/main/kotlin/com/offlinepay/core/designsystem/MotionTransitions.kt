package com.offlinepay.core.designsystem

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

// ---------------------------------------------------------------------------
// Material Motion transition utilities
//
// Design reference: Section 10.3, 10.4
// Requirements: Req 15.1, Req 15.6, Req 15.7
// ---------------------------------------------------------------------------

/**
 * **Shared Axis Z — forward enter transition.**
 *
 * Implements the Material Motion Shared Axis pattern along the Z axis,
 * used for hierarchical forward navigation (e.g., list → detail).
 *
 * The incoming screen slides in from the right (1/4 of its width) while
 * simultaneously fading in over 300ms with the standard
 * [FastOutSlowInEasing] easing curve.
 *
 * Design reference: Section 10.4 — Shared Axis Z forward
 * Requirements: Req 15.6
 *
 * ### Usage (Compose NavHost)
 * ```kotlin
 * NavHost(
 *     enterTransition = { sharedAxisZEnterTransition() },
 *     exitTransition  = { sharedAxisZExitTransition() },
 * ) { … }
 * ```
 *
 * @return [EnterTransition] combining a horizontal slide-in and fade-in.
 */
fun sharedAxisZEnterTransition(): EnterTransition =
    slideInHorizontally(
        initialOffsetX = { fullWidth -> fullWidth / 4 },
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
    ) + fadeIn(
        animationSpec = tween(durationMillis = 300),
    )

/**
 * **Shared Axis Z — forward exit transition.**
 *
 * The outgoing screen slides out to the left (1/4 of its width) while
 * simultaneously fading out over 250ms.
 *
 * Design reference: Section 10.4 — Shared Axis Z forward
 * Requirements: Req 15.6
 *
 * @return [ExitTransition] combining a horizontal slide-out and fade-out.
 */
fun sharedAxisZExitTransition(): ExitTransition =
    slideOutHorizontally(
        targetOffsetX = { fullWidth -> -fullWidth / 4 },
        animationSpec = tween(durationMillis = 250),
    ) + fadeOut(
        animationSpec = tween(durationMillis = 250),
    )

/**
 * **Fade Through — enter transition.**
 *
 * Implements the Material Motion Fade Through pattern, used for transitions
 * between screens that have no explicit spatial relationship
 * (e.g., bottom-nav tab switches).
 *
 * The incoming screen fades in over 200ms.
 *
 * Design reference: Section 10.4 — Fade Through
 * Requirements: Req 15.7
 *
 * ### Usage (Compose NavHost)
 * ```kotlin
 * NavHost(
 *     enterTransition = { fadeThroughEnterTransition() },
 *     exitTransition  = { fadeThroughExitTransition() },
 * ) { … }
 * ```
 *
 * @return [EnterTransition] that fades the incoming screen in.
 */
fun fadeThroughEnterTransition(): EnterTransition =
    fadeIn(animationSpec = tween(durationMillis = 200))

/**
 * **Fade Through — exit transition.**
 *
 * The outgoing screen fades out over 200ms.
 *
 * Design reference: Section 10.4 — Fade Through
 * Requirements: Req 15.7
 *
 * @return [ExitTransition] that fades the outgoing screen out.
 */
fun fadeThroughExitTransition(): ExitTransition =
    fadeOut(animationSpec = tween(durationMillis = 200))
