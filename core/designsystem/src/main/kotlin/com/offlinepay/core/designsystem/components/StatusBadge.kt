package com.offlinepay.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offlinepay.core.designsystem.ErrorContainer
import com.offlinepay.core.designsystem.OnErrorContainer
import com.offlinepay.core.designsystem.OnSuccessContainer
import com.offlinepay.core.designsystem.OnWarningContainer
import com.offlinepay.core.designsystem.SuccessContainer
import com.offlinepay.core.designsystem.UnknownGrey
import com.offlinepay.core.designsystem.WarningContainer

/**
 * Payment transaction status values.
 *
 * Design reference: Section 3.2, 10.5 — StatusBadge
 * Requirements: Req 14.5, Req 14.8, Req 15.1
 */
enum class PaymentStatus {
    /** Transaction completed successfully. */
    SUCCESS,

    /** Transaction definitively failed. */
    FAILURE,

    /** Transaction initiated but outcome is not yet confirmed (e.g. 123PAY). */
    PENDING,

    /** Status cannot be determined (e.g. USSD response was ambiguous). */
    UNKNOWN,
}

/** Resolved visual tokens for a [PaymentStatus]. */
private data class StatusVisuals(
    val backgroundColor: Color,
    val contentColor: Color,
    val label: String,
)

private fun resolveVisuals(status: PaymentStatus): StatusVisuals = when (status) {
    PaymentStatus.SUCCESS -> StatusVisuals(
        backgroundColor = SuccessContainer,
        contentColor = OnSuccessContainer,
        label = "Success",
    )
    PaymentStatus.FAILURE -> StatusVisuals(
        backgroundColor = ErrorContainer,
        contentColor = OnErrorContainer,
        label = "Failed",
    )
    PaymentStatus.PENDING -> StatusVisuals(
        backgroundColor = WarningContainer,
        contentColor = OnWarningContainer,
        label = "Pending",
    )
    PaymentStatus.UNKNOWN -> StatusVisuals(
        // Neutral grey pill — no dedicated container token, compose directly.
        backgroundColor = Color(0xFFF1F5F9),
        contentColor = UnknownGrey,
        label = "Unknown",
    )
}

/**
 * Pill-shaped badge that communicates a payment [PaymentStatus] visually and
 * accessibly, with a live-region announcement so TalkBack announces status changes
 * without requiring the user to navigate to the badge.
 *
 * Colours:
 * - SUCCESS  → green container / on-success-container text
 * - FAILURE  → red container / on-error-container text
 * - PENDING  → amber container / on-warning-container text
 * - UNKNOWN  → neutral grey
 *
 * Semantics: `liveRegion = LiveRegionMode.Polite` — TalkBack will announce the
 * status text after the current utterance finishes (Req 15.1).
 *
 * Design reference: Section 3.2, 10.5 — StatusBadge accessibility
 * Requirements: Req 14.5, Req 14.8, Req 15.1
 *
 * ### Usage
 * ```kotlin
 * StatusBadge(status = PaymentStatus.SUCCESS)
 * StatusBadge(status = PaymentStatus.PENDING, modifier = Modifier.align(Alignment.End))
 * ```
 *
 * @param status   The [PaymentStatus] to display.
 * @param modifier Modifier applied to the pill container.
 */
@Composable
fun StatusBadge(
    status: PaymentStatus,
    modifier: Modifier = Modifier,
) {
    val visuals = resolveVisuals(status)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50)) // full pill
            .background(visuals.backgroundColor)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = visuals.label
            }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = visuals.label,
            color = visuals.contentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp,
            maxLines = 1,
        )
    }
}
