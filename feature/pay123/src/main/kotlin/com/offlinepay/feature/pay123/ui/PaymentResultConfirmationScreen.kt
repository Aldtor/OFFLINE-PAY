package com.offlinepay.feature.pay123.ui

import android.view.HapticFeedbackConstants
import android.view.WindowManager.LayoutParams.FLAG_SECURE
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinepay.core.common.format.UpiAmountFormatter
import com.offlinepay.core.designsystem.GlassCard
import com.offlinepay.core.designsystem.LocalOfflinePayColors
import com.offlinepay.core.designsystem.OfflinePayBackground

/**
 * Screen shown when the user dismisses the 123PAY result prompt without confirming
 * (leaving the transaction in UNKNOWN status — Req 6.8).
 *
 * ## Purpose
 * This "deferred resolution" screen allows the user to retrospectively mark the
 * transaction as succeeded or failed once they know the outcome (e.g., by checking
 * their bank balance or SMS confirmation).
 *
 * ## What it shows
 * - Prominent amber "Payment status unknown" warning header with icon (Req 12.9)
 * - Transaction timestamp (Req 6.8)
 * - Merchant name, UPI ID, and formatted amount
 * - "Mark as Succeeded" button → calls [UpdateTransactionStatusUseCase](SUCCESS) → navigates to `payment_success/{transactionId}` (Req 6.7)
 * - "Mark as Failed" button → calls [UpdateTransactionStatusUseCase](FAILURE) → navigates to `payment_failure` (Req 6.8)
 * - "View Receipt" text link → navigates to `transaction_receipt/{transactionId}` (Req 12.5)
 *
 * ## Security
 * [FLAG_SECURE] is applied locally as defence-in-depth (the global [SecurityWindowController]
 * in `MainActivity` also covers this route). This prevents screenshots and screen recording
 * during the sensitive deferred-resolution flow (Req 9.23).
 *
 * Design reference: Section 10.1 (payment_result_confirmation route), Section 13.2
 * Requirements: Req 6.7, Req 6.8, Req 12.5, Req 12.9
 *
 * @param onNavigateToSuccess  Called with `transactionId` when user marks the payment as succeeded.
 *                             Should navigate to `payment_success/{transactionId}`.
 * @param onNavigateToFailure  Called when user marks the payment as failed.
 *                             Should navigate to `payment_failure`.
 * @param onNavigateToReceipt  Called with `transactionId` when user taps "View Receipt".
 *                             Should navigate to `transaction_receipt/{transactionId}`.
 * @param viewModel            Hilt-injected [PaymentResultConfirmationViewModel].
 */
@Composable
fun PaymentResultConfirmationScreen(
    onNavigateToSuccess: (transactionId: String) -> Unit,
    onNavigateToFailure: () -> Unit,
    onNavigateToReceipt: (transactionId: String) -> Unit,
    viewModel: PaymentResultConfirmationViewModel = hiltViewModel(),
) {
    // ── FLAG_SECURE (defence-in-depth) ────────────────────────────────────────
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? android.app.Activity)?.window
        window?.addFlags(FLAG_SECURE)
        onDispose {
            window?.clearFlags(FLAG_SECURE)
        }
    }

    // ── Collect state ─────────────────────────────────────────────────────────
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // ── Collect one-shot navigation events ────────────────────────────────────
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PaymentResultConfirmationEvent.NavigateToSuccess ->
                    onNavigateToSuccess(event.transactionId)

                is PaymentResultConfirmationEvent.NavigateToFailure ->
                    onNavigateToFailure()

                is PaymentResultConfirmationEvent.NavigateToReceipt ->
                    onNavigateToReceipt(event.transactionId)
            }
        }
    }

    // ── Render by state ───────────────────────────────────────────────────────
    OfflinePayBackground {
        AnimatedContent(
            targetState = uiState,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "payment_result_confirmation_content",
        ) { state ->
        when (state) {
            is PaymentResultConfirmationUiState.Loading ->
                ResultConfirmationLoadingContent()

            is PaymentResultConfirmationUiState.Error ->
                ResultConfirmationErrorContent(message = state.message)

            is PaymentResultConfirmationUiState.Ready ->
                ResultConfirmationReadyContent(
                    state = state,
                    onMarkAsSucceeded = {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        viewModel.onAction(PaymentResultConfirmationAction.MarkAsSucceeded)
                    },
                    onMarkAsFailed = {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        viewModel.onAction(PaymentResultConfirmationAction.MarkAsFailed)
                    },
                    onViewReceipt = {
                        viewModel.onAction(PaymentResultConfirmationAction.ViewReceipt)
                    },
                )
        }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Loading
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ResultConfirmationLoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Error
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ResultConfirmationErrorContent(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Ready content
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Main content composable rendered when the [TransactionRecord] is loaded.
 *
 * Layout (single-column, scrollable):
 *  1. Prominent amber "Payment status unknown" warning header
 *  2. Merchant details card (name, UPI ID, amount, timestamp)
 *  3. "Mark as Succeeded" primary button
 *  4. "Mark as Failed" outlined button
 *  5. "View Receipt" text link
 *
 * Accessibility:
 * - The warning header is announced as a live region politely so TalkBack users
 *   are immediately aware of the ambiguous status.
 * - Both deferred-action buttons have distinct content descriptions.
 * - Buttons are disabled (not gone) while [state.isUpdating] is true — preserves
 *   layout stability and signals loading state to assistive technology.
 *
 * @param state            Current [PaymentResultConfirmationUiState.Ready] snapshot.
 * @param onMarkAsSucceeded Called when user taps "Mark as Succeeded".
 * @param onMarkAsFailed    Called when user taps "Mark as Failed".
 * @param onViewReceipt     Called when user taps "View Receipt".
 */
@Composable
private fun ResultConfirmationReadyContent(
    state: PaymentResultConfirmationUiState.Ready,
    onMarkAsSucceeded: () -> Unit,
    onMarkAsFailed: () -> Unit,
    onViewReceipt: () -> Unit,
) {
    val colors = LocalOfflinePayColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {

        // ─── Warning header ────────────────────────────────────────────────
        UnknownStatusHeader()

        // ─── Transaction details card ──────────────────────────────────────
        ResultTransactionDetailsCard(state = state)

        Spacer(modifier = Modifier.height(4.dp))

        // ─── Deferred action buttons ───────────────────────────────────────
        // "Mark as Succeeded" — primary action, success green styling
        Button(
            onClick = onMarkAsSucceeded,
            enabled = !state.isUpdating,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Mark this payment as succeeded"
                },
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.successGreen,
                disabledContainerColor = colors.successGreen.copy(alpha = 0.38f),
            ),
        ) {
            if (state.isUpdating) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(16.dp)
                        .padding(end = 8.dp),
                    color = colors.brandOnPrimary,
                    strokeWidth = 2.dp,
                )
            }
            Text(
                text = "✅  Mark as Succeeded",
                style = MaterialTheme.typography.labelLarge,
            )
        }

        // "Mark as Failed" — secondary action, outlined with error red tint
        OutlinedButton(
            onClick = onMarkAsFailed,
            enabled = !state.isUpdating,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Mark this payment as failed"
                },
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = if (!state.isUpdating) colors.errorRed
                else colors.errorRed.copy(alpha = 0.38f),
            ),
        ) {
            Text(
                text = "❌  Mark as Failed",
                style = MaterialTheme.typography.labelLarge,
                color = if (!state.isUpdating) colors.errorRed
                else colors.errorRed.copy(alpha = 0.38f),
            )
        }

        // ─── View Receipt text link ────────────────────────────────────────
        // Req 12.5: link to transaction receipt without changing status
        TextButton(
            onClick = onViewReceipt,
            modifier = Modifier.semantics {
                contentDescription = "View transaction receipt"
            },
        ) {
            Text(
                text = "View Receipt",
                style = MaterialTheme.typography.labelLarge,
                color = colors.infoBlue,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Warning header
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Prominent amber warning block that anchors the screen visually.
 *
 * Layout:
 * - Warning icon (amber, 48dp)
 * - "Payment status unknown" headline in headlineSmall (amber)
 * - Supporting subtext explaining what the user should do
 *
 * Accessibility:
 * - The combined block is a single semantics node with a descriptive
 *   `contentDescription` for TalkBack traversal.
 */
@Composable
private fun UnknownStatusHeader() {
    val colors = LocalOfflinePayColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = colors.warningContainer,
                shape = RoundedCornerShape(16.dp),
            )
            .border(
                width = 1.5.dp,
                color = colors.warningAmber,
                shape = RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .semantics(mergeDescendants = true) {
                contentDescription =
                    "Payment status unknown. You dismissed the 123PAY confirmation without confirming the result. Please mark the payment as succeeded or failed once you know the outcome."
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Warning icon
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null, // covered by parent mergedSemantics
            tint = colors.warningAmber,
            modifier = Modifier.size(48.dp),
        )

        // Headline
        Text(
            text = "Payment status unknown",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                color = colors.onWarningContainer,
            ),
            textAlign = TextAlign.Center,
        )

        // Supporting text
        Text(
            text = "You dismissed the payment confirmation without answering. " +
                "Check your bank balance or SMS and mark the payment correctly below.",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = colors.onWarningContainer,
            ),
            textAlign = TextAlign.Center,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Transaction details card
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Card displaying the transaction's key fields below the warning header.
 *
 * Rows:
 * - Transaction ID (truncated: first 8 chars + "…")
 * - Merchant (display name)
 * - UPI ID
 * - Amount (formatted as ₹ with Indian locale via [UpiAmountFormatter])
 * - Date & Time (formatted from [timestampMs])
 */
@Composable
private fun ResultTransactionDetailsCard(
    state: PaymentResultConfirmationUiState.Ready,
) {
    val formattedDateTime = rememberFormattedDateTime(state.timestampMs)

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        elevated = true,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            ResultDetailRow(
                label = "Transaction ID",
                value = state.truncatedId,
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            ResultDetailRow(
                label = "Merchant",
                value = state.merchantName,
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            ResultDetailRow(
                label = "UPI ID",
                value = state.upiId,
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            ResultDetailRow(
                label = "Amount",
                value = UpiAmountFormatter.formatPaiseToRupees(state.amountPaise),
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            // Transaction timestamp — Req 6.8
            ResultDetailRow(
                label = "Date & Time",
                value = formattedDateTime,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Detail row
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A single label–value row matching the style used throughout result screens.
 */
@Composable
private fun ResultDetailRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1.5f)
                .padding(start = 8.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Date/time formatter helper
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Formats an epoch-millisecond timestamp into a human-readable date/time string.
 *
 * Example output: "25 Jan 2025, 14:30"
 *
 * Uses [java.text.SimpleDateFormat] with `Locale("en", "IN")` matching the
 * Indian locale conventions used throughout OfflinePay for financial data display.
 *
 * @param timestampMs Epoch milliseconds from the [TransactionRecord].
 * @return Formatted date/time string suitable for display in the transaction card.
 */
@Composable
private fun rememberFormattedDateTime(timestampMs: Long): String =
    androidx.compose.runtime.remember(timestampMs) {
        try {
            val sdf = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale("en", "IN"))
            sdf.format(java.util.Date(timestampMs))
        } catch (_: Exception) {
            "—"
        }
    }
