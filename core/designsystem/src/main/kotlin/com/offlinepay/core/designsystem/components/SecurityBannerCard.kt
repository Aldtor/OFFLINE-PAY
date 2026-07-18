package com.offlinepay.core.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------------------
// Internal design constants
// ---------------------------------------------------------------------------

/** Amber warning banner tokens (dismissible). */
private val AmberBannerBackground = Color(0xFFFEF3C7)   // WarningContainer
private val AmberBannerContent = Color(0xFF78350F)       // OnWarningContainer (dark amber)
private val AmberBannerIcon = Color(0xFFD97706)          // WarningAmber

/** Red critical banner tokens (persistent). */
private val RedBannerBackground = Color(0xFFFEE2E2)      // ErrorContainer
private val RedBannerContent = Color(0xFF7F1D1D)         // OnErrorContainer (dark red)
private val RedBannerIcon = Color(0xFFDC2626)            // ErrorRed

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * Dismissible amber security warning banner.
 *
 * Shown for non-critical security notices (e.g. stale integrity verdict, developer
 * options enabled). The user may dismiss it with the "×" button. Uses
 * [AnimatedVisibility] with a vertical expand/shrink + fade transition so the banner
 * slides in/out gracefully rather than popping on/off.
 *
 * Design reference: Section 3.2 — SecurityBannerCard (dismissible amber variant)
 * Requirements: Req 14.5, Req 14.8, Req 15.1
 *
 * ### Usage
 * ```kotlin
 * var showWarning by remember { mutableStateOf(true) }
 *
 * DismissibleSecurityBanner(
 *     visible = showWarning,
 *     message = "Integrity check is outdated. Tap to refresh.",
 *     onDismiss = { showWarning = false },
 * )
 * ```
 *
 * @param visible   Whether the banner is currently shown. Drive with a `State<Boolean>`
 *                  so [AnimatedVisibility] can animate the transition.
 * @param message   Warning message text shown to the user.
 * @param onDismiss Called when the user taps the dismiss (×) button.
 * @param modifier  Modifier applied to the [AnimatedVisibility] wrapper.
 */
@Composable
fun DismissibleSecurityBanner(
    visible: Boolean,
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandVertically(animationSpec = tween(280)) + fadeIn(animationSpec = tween(280)),
        exit = shrinkVertically(animationSpec = tween(220)) + fadeOut(animationSpec = tween(220)),
    ) {
        SecurityBannerContent(
            message = message,
            backgroundColor = AmberBannerBackground,
            contentColor = AmberBannerContent,
            iconTint = AmberBannerIcon,
            dismissible = true,
            onDismiss = onDismiss,
        )
    }
}

/**
 * Persistent red critical security banner.
 *
 * Shown for critical security failures (e.g. Play Integrity BLOCKED, tamper detected).
 * Cannot be dismissed by the user. Uses [AnimatedVisibility] for show/hide transitions,
 * but the banner itself offers no dismiss action.
 *
 * Design reference: Section 3.2 — SecurityBannerCard (persistent red variant)
 * Requirements: Req 14.5, Req 14.8, Req 15.1
 *
 * ### Usage
 * ```kotlin
 * PersistentSecurityBanner(
 *     visible = tamperDetected,
 *     message = "Security check failed. Payments are blocked.",
 * )
 * ```
 *
 * @param visible  Whether the banner is currently shown.
 * @param message  Critical warning message text shown to the user.
 * @param modifier Modifier applied to the [AnimatedVisibility] wrapper.
 */
@Composable
fun PersistentSecurityBanner(
    visible: Boolean,
    message: String,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandVertically(animationSpec = tween(280)) + fadeIn(animationSpec = tween(280)),
        exit = shrinkVertically(animationSpec = tween(220)) + fadeOut(animationSpec = tween(220)),
    ) {
        SecurityBannerContent(
            message = message,
            backgroundColor = RedBannerBackground,
            contentColor = RedBannerContent,
            iconTint = RedBannerIcon,
            dismissible = false,
            onDismiss = {},
        )
    }
}

// ---------------------------------------------------------------------------
// Internal shared layout
// ---------------------------------------------------------------------------

/**
 * Internal layout shared by both banner variants.
 *
 * Renders a rounded-corner card-like row with a warning icon on the left,
 * the [message] text expanding to fill available space, and — when [dismissible]
 * is `true` — a close [IconButton] on the right.
 *
 * The entire banner is a single accessibility node: the icon is hidden from
 * TalkBack (`clearAndSetSemantics`) and the container announces the full
 * [message] text via `contentDescription`.
 */
@Composable
private fun SecurityBannerContent(
    message: String,
    backgroundColor: Color,
    contentColor: Color,
    iconTint: Color,
    dismissible: Boolean,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .semantics { contentDescription = message }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null, // announced via parent semantics
                tint = iconTint,
                modifier = Modifier
                    .size(20.dp)
                    .clearAndSetSemantics { },
            )

            Spacer(Modifier.width(10.dp))

            Text(
                text = message,
                color = contentColor,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )

            if (dismissible) {
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Dismiss security warning",
                        tint = contentColor,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
