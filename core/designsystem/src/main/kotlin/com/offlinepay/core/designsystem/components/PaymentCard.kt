package com.offlinepay.core.designsystem.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.offlinepay.core.designsystem.GlassCard

/**
 * OfflinePay payment card — now a frosted-glass surface (premium redesign).
 *
 * Used wherever a payment-related surface (confirmation card, history row card,
 * merchant card, receipt card) is needed. Delegates to [GlassCard] so every
 * existing call site automatically inherits the translucent glass treatment —
 * hairline border, top sheen and soft shadow over the ambient aurora background —
 * while keeping the exact same public signature.
 *
 * Design reference: Glass / Apple-premium redesign — cards use a 20dp radius here
 * (slightly tighter than the default [GlassCard] 26dp) to preserve the compact
 * feel of dense payment/history rows.
 * Requirements: Req 14.5 (card component), Req 14.8 (shape consistency)
 *
 * ### Usage
 * ```kotlin
 * PaymentCard(
 *     onClick = { /* navigate to detail */ },
 *     modifier = Modifier.fillMaxWidth().padding(OfflinePaySpacing.M),
 * ) {
 *     Column(Modifier.padding(OfflinePaySpacing.M)) {
 *         Text("Payment details")
 *     }
 * }
 * ```
 *
 * @param modifier  Modifier applied to the card container.
 * @param onClick   Optional click handler. When `null` the card is non-interactive
 *                  and TalkBack will not announce it as a button.
 * @param content   Composable content rendered inside the card.
 */
@Composable
fun PaymentCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    GlassCard(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        onClick = onClick,
        content = content,
    )
}
