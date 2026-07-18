package com.offlinepay.feature.payment.ui

import android.view.HapticFeedbackConstants
import android.view.WindowManager.LayoutParams.FLAG_SECURE
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinepay.core.common.format.UpiAmountFormatter
import com.offlinepay.core.designsystem.GlassCard
import com.offlinepay.core.designsystem.LocalOfflinePayColors
import com.offlinepay.core.designsystem.OfflinePayBackground
import com.offlinepay.core.designsystem.bouncySpring
import com.offlinepay.core.domain.model.PaymentMethodType
import kotlinx.coroutines.delay

/**
 * Payment success screen composable — shared between USSD and 123PAY outcomes.
 *
 * Displays:
 * - Animated checkmark (scaleIn + fadeIn via [AnimatedVisibility])
 * - Transaction ID (truncated: first 8 chars + "…")
 * - Merchant name
 * - Amount formatted in rupees (₹)
 * - Payment method label ("Paid via USSD" / "Paid via 123PAY")
 * - "View Receipt" button (navigates to transaction_receipt screen)
 * - "Pay Again" button (navigates back to scanner)
 *
 * Security:
 * - [FLAG_SECURE] is active while this screen is visible, preventing screenshots and
 *   screen recording (Req 9.23). This is also enforced globally by
 *   [SecurityWindowController] in MainActivity, but we apply it locally here as a
 *   defence-in-depth measure for the feature module.
 *
 * Haptic feedback:
 * - On screen enter: [HapticFeedbackConstants.TEXT_HANDLE_MOVE] × 2 with a 100 ms gap,
 *   triggered via [LaunchedEffect] (Req: Task 26.1 haptic spec).
 *
 * Design reference: Section 10.1 (payment_success route), Section 13.2
 * Requirements: Req 5.9, Req 6.6, Req 8.14, Req 9.23, Req 12.5
 *
 * @param onNavigateToReceipt  Called with the transactionId to navigate to receipt screen.
 * @param onNavigateToScanner  Called when "Pay Again" is tapped — pops back to scanner.
 * @param viewModel            Hilt-injected; override in previews and tests.
 */
@Composable
fun PaymentSuccessScreen(
    onNavigateToReceipt: (transactionId: String) -> Unit,
    onNavigateToScanner: () -> Unit,
    viewModel: PaymentSuccessViewModel = hiltViewModel(),
) {
    // ── FLAG_SECURE (defence-in-depth — SecurityWindowController covers globally) ──
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? android.app.Activity)?.window
        window?.addFlags(FLAG_SECURE)
        onDispose {
            window?.clearFlags(FLAG_SECURE)
        }
    }

    // ── Haptic feedback — TextHandleMove × 2 with 100 ms gap on screen enter ──
    LaunchedEffect(Unit) {
        view.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE)
        delay(100L)
        view.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE)
    }

    // ── Collect state and one-shot events ─────────────────────────────────────
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PaymentSuccessEvent.NavigateToReceipt ->
                    onNavigateToReceipt(event.transactionId)

                is PaymentSuccessEvent.NavigateToScanner ->
                    onNavigateToScanner()
            }
        }
    }

    // ── Render by state ───────────────────────────────────────────────────────
    OfflinePayBackground {
        when (val state = uiState) {
            is PaymentSuccessUiState.Loading -> SuccessLoadingContent()
            is PaymentSuccessUiState.Error -> SuccessErrorContent(message = state.message)
            is PaymentSuccessUiState.Success -> SuccessContent(
                state = state,
                onViewReceipt = viewModel::onViewReceipt,
                onPayAgain = viewModel::onPayAgain,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Loading
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SuccessLoadingContent() {
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
private fun SuccessErrorContent(message: String) {
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
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Success content
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Main content rendered when the [TransactionRecord] is loaded successfully.
 *
 * Layout (single-column, scrollable):
 *  1. Animated checkmark icon (successGreen, 96dp, scaleIn + fadeIn)
 *  2. "Payment Successful!" headline
 *  3. Transaction details card (divider-separated rows)
 *  4. "View Receipt" filled button
 *  5. "Pay Again" outlined button
 */
@Composable
private fun SuccessContent(
    state: PaymentSuccessUiState.Success,
    onViewReceipt: () -> Unit,
    onPayAgain: () -> Unit,
) {
    // Control AnimatedVisibility — visible=true triggers on first composition
    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { contentVisible = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Animated checkmark
        AnimatedCheckmark(visible = contentVisible)

        // Headline
        Text(
            text = "Payment Successful!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        // Transaction details card
        TransactionDetailsCard(state = state)

        Spacer(modifier = Modifier.height(8.dp))

        // Primary CTA: View Receipt
        Button(
            onClick = onViewReceipt,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = LocalOfflinePayColors.current.successGreen,
            ),
        ) {
            Text(text = "View Receipt")
        }

        // Secondary CTA: Pay Again
        OutlinedButton(
            onClick = onPayAgain,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "Pay Again")
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Animated checkmark
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Large checkmark icon that enters with [scaleIn] + [fadeIn] via [AnimatedVisibility].
 *
 * The enter animation uses a 400 ms spring-like ease (tween with easeOut-like decel)
 * for a satisfying "pop" that conveys success clearly. Color is [successGreen]
 * (#16A34A) from [LocalOfflinePayColors] as required by Task 26.1.
 *
 * @param visible Controls whether the icon is visible. Set to `true` on first composition
 *                to trigger the enter animation.
 */
@Composable
private fun AnimatedCheckmark(visible: Boolean) {
    val successGreen = LocalOfflinePayColors.current.successGreen

    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(
            animationSpec = bouncySpring(),
            initialScale = 0.3f,
        ) + fadeIn(
            animationSpec = tween(durationMillis = 300),
        ),
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = "Payment Successful",
            tint = successGreen,
            modifier = Modifier.size(96.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Transaction details card
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Card displaying the key transaction details in a list of labelled rows.
 *
 * Rows:
 * - Transaction ID (truncated: first 8 chars + "…")
 * - Merchant
 * - Amount (formatted as ₹ with Indian locale)
 * - Method (e.g. "Paid via USSD" or "Paid via 123PAY")
 */
@Composable
private fun TransactionDetailsCard(state: PaymentSuccessUiState.Success) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        elevated = true,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            DetailRow(
                label = "Transaction ID",
                value = state.truncatedId,
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            DetailRow(
                label = "Merchant",
                value = state.merchantName,
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            DetailRow(
                label = "Amount",
                value = UpiAmountFormatter.formatPaiseToRupees(state.amountPaise),
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            DetailRow(
                label = "Method",
                value = when (state.paymentMethod) {
                    PaymentMethodType.USSD -> "Paid via USSD"
                    PaymentMethodType.PAY123 -> "Paid via 123PAY"
                },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Detail row
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A single label–value row used inside [TransactionDetailsCard].
 *
 * The label is rendered in [MaterialTheme.typography.labelMedium] with
 * [MaterialTheme.colorScheme.onSurfaceVariant] to create a secondary hierarchy,
 * while the value uses [MaterialTheme.typography.bodyMedium] with the primary
 * on-surface color for readability.
 */
@Composable
private fun DetailRow(
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1.5f)
                .padding(start = 8.dp),
        )
    }
}
