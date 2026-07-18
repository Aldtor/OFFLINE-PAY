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
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinepay.core.designsystem.GlassCard
import com.offlinepay.core.designsystem.LocalOfflinePayColors
import com.offlinepay.core.designsystem.OfflinePayBackground

/**
 * Payment failure screen composable — shared between USSD and 123PAY failure outcomes.
 *
 * Displays:
 * - Animated error icon (scaleIn + fadeIn via [AnimatedVisibility])
 * - Failure title derived from [DomainError] type
 * - Descriptive failure message per error → UI mapping table (design Section 13.2)
 * - "Retry" button (re-attempt the same payment method)
 * - "Try 123PAY" / "Try USSD" fallback action (when a fallback method is available)
 *
 * Security:
 * - [FLAG_SECURE] is active while this screen is visible (Req 9.23).
 *
 * Haptic feedback:
 * - On screen enter: [HapticFeedbackConstants.LONG_PRESS] (Task 26.2 spec).
 *
 * Design reference: Section 10.1 (payment_failure route), Section 13.2
 * Requirements: Req 5.6, Req 5.10, Req 6.7, Req 12.5, Req 12.9
 *
 * @param onRetry           Called when the user taps "Retry" to re-attempt the same method.
 * @param onNavigateToPay123 Called when the user taps "Try 123PAY" as fallback.
 * @param onNavigateToUssd   Called when the user taps "Try USSD" as fallback.
 * @param viewModel          Hilt-injected; override in previews and tests.
 */
@Composable
fun PaymentFailureScreen(
    onRetry: () -> Unit,
    onNavigateToPay123: () -> Unit,
    onNavigateToUssd: () -> Unit,
    viewModel: PaymentFailureViewModel = hiltViewModel(),
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

    // ── Haptic feedback — LongPress on screen enter ──
    LaunchedEffect(Unit) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    // ── Collect state and one-shot events ─────────────────────────────────────
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PaymentFailureEvent.Retry -> onRetry()
                is PaymentFailureEvent.NavigateToPay123 -> onNavigateToPay123()
                is PaymentFailureEvent.NavigateToUssd -> onNavigateToUssd()
            }
        }
    }

    // ── Render by state ───────────────────────────────────────────────────────
    OfflinePayBackground {
        when (val state = uiState) {
            is PaymentFailureUiState.Loading -> FailureLoadingContent()
            is PaymentFailureUiState.Error -> FailureErrorContent(message = state.message)
            is PaymentFailureUiState.Ready -> FailureContent(
                state = state,
                onRetry = viewModel::onRetry,
                onTryPay123 = viewModel::onTryPay123,
                onTryUssd = viewModel::onTryUssd,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Loading
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FailureLoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Error (missing args — should not occur in production)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FailureErrorContent(message: String) {
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
// Failure content
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FailureContent(
    state: PaymentFailureUiState.Ready,
    onRetry: () -> Unit,
    onTryPay123: () -> Unit,
    onTryUssd: () -> Unit,
) {
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
        // Animated error icon
        AnimatedErrorIcon(visible = contentVisible)

        // Failure title
        Text(
            text = state.failureTitle,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )

        // Failure message
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = state.failureMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(20.dp),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Primary CTA: Retry
        Button(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Text(text = "Retry")
        }

        // Secondary CTA: Fallback method (if available)
        when (state.fallbackMethod) {
            FallbackMethod.TRY_PAY123 -> {
                OutlinedButton(
                    onClick = onTryPay123,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text(text = "Try 123PAY")
                }
            }

            FallbackMethod.TRY_USSD -> {
                OutlinedButton(
                    onClick = onTryUssd,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text(text = "Try USSD")
                }
            }

            FallbackMethod.NONE -> {
                // No fallback action available
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Animated error icon
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AnimatedErrorIcon(visible: Boolean) {
    val errorRed = LocalOfflinePayColors.current.errorRed

    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(
            animationSpec = tween(durationMillis = 400),
            initialScale = 0.3f,
        ) + fadeIn(
            animationSpec = tween(durationMillis = 300),
        ),
    ) {
        Icon(
            imageVector = Icons.Filled.Error,
            contentDescription = "Payment Failed",
            tint = errorRed,
            modifier = Modifier
                .size(96.dp)
                .semantics { contentDescription = "Error icon indicating payment failure" },
        )
    }
}
