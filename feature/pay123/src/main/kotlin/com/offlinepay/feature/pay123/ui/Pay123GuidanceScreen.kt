package com.offlinepay.feature.pay123.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinepay.core.designsystem.GlassScaffold
import com.offlinepay.core.designsystem.LocalOfflinePayColors
import com.offlinepay.core.designsystem.OfflinePayTheme
import com.offlinepay.core.designsystem.components.MerchantAvatar
import com.offlinepay.feature.pay123.Pay123ViewModel

/**
 * Full-screen composable for the 123PAY IVR guidance flow.
 *
 * ### States handled:
 * - **CallInProgress** — shows animated "CALL IN PROGRESS" indicator, merchant avatar,
 *   UPI ID, formatted amount, numbered IVR step list, and a warning not to close the screen.
 * - **AwaitingConfirmation** — overlays a "Did your payment succeed?" [AlertDialog] with
 *   three action buttons: "Yes, Succeeded" ([Pay123ViewModel.UiAction.ConfirmSuccess]),
 *   "No, Failed" ([Pay123ViewModel.UiAction.ConfirmFailure]),
 *   "Not sure" ([Pay123ViewModel.UiAction.DismissWithoutConfirming]).
 * - **Other states** — screen is empty (navigation away is handled by [UiEvent]s).
 *
 * ### Security
 * [WindowManager.LayoutParams.FLAG_SECURE] is applied on entry and cleared on dispose,
 * preventing screenshots and screen recording during the sensitive payment operation.
 *
 * ### Lifecycle wiring
 * [Pay123ViewModel.attachLifecycle] is called once inside `LaunchedEffect(Unit)` so the
 * session state machine can observe `onPause()` / `onResume()` to drive the
 * `CALL_IN_PROGRESS → AWAITING_CONFIRMATION` transition.
 *
 * Design reference: Section 7.3 (Guidance Screen Design)
 * Requirements: Req 6.1, Req 6.2, Req 6.3, Req 6.4, Req 6.5, Req 6.6, Req 6.7, Req 6.8
 *
 * @param onNavigateToSuccess         Called when [Pay123ViewModel.UiEvent.NavigateToSuccess] is received.
 * @param onNavigateToFailure         Called when [Pay123ViewModel.UiEvent.NavigateToFailure] is received.
 * @param onNavigateToResultConfirmation Called when [Pay123ViewModel.UiEvent.NavigateToResultConfirmation]
 *                                    is received (user dismissed without confirming — UNKNOWN status).
 * @param viewModel                   The backing [Pay123ViewModel] (injected by Hilt by default).
 */
@Composable
fun Pay123GuidanceScreen(
    onNavigateToSuccess: (transactionId: String) -> Unit = {},
    onNavigateToFailure: () -> Unit = {},
    onNavigateToResultConfirmation: (transactionId: String) -> Unit = {},
    viewModel: Pay123ViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    // -------------------------------------------------------------------------
    // FLAG_SECURE: apply when entering, remove when leaving
    // -------------------------------------------------------------------------
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    // -------------------------------------------------------------------------
    // Wire lifecycle observer so the state machine detects onPause / onResume
    // -------------------------------------------------------------------------
    LaunchedEffect(Unit) {
        viewModel.attachLifecycle(lifecycleOwner.lifecycle)
    }

    // -------------------------------------------------------------------------
    // Collect one-shot navigation events
    // -------------------------------------------------------------------------
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is Pay123ViewModel.UiEvent.NavigateToSuccess ->
                    onNavigateToSuccess(event.transactionId)

                is Pay123ViewModel.UiEvent.NavigateToFailure ->
                    onNavigateToFailure()

                is Pay123ViewModel.UiEvent.NavigateToResultConfirmation ->
                    onNavigateToResultConfirmation(event.transactionId)

                is Pay123ViewModel.UiEvent.LaunchPhoneCall -> {
                    // Phone call is launched by Pay123Controller / the ViewModel;
                    // no UI-side navigation needed here.
                }
            }
        }
    }

    OfflinePayTheme {
        Pay123GuidanceContent(
            uiState = uiState,
            onConfirmSuccess = { viewModel.onAction(Pay123ViewModel.UiAction.ConfirmSuccess) },
            onConfirmFailure = { viewModel.onAction(Pay123ViewModel.UiAction.ConfirmFailure) },
            onDismissWithoutConfirming = {
                viewModel.onAction(Pay123ViewModel.UiAction.DismissWithoutConfirming)
            },
        )
    }
}

// ─── Internal content composable ─────────────────────────────────────────────

/**
 * Stateless inner composable for the IVR guidance content.
 *
 * Extracted to allow Compose Previews without a real ViewModel.
 */
@Composable
internal fun Pay123GuidanceContent(
    uiState: Pay123ViewModel.UiState,
    onConfirmSuccess: () -> Unit,
    onConfirmFailure: () -> Unit,
    onDismissWithoutConfirming: () -> Unit,
) {
    val colors = LocalOfflinePayColors.current

    // -------------------------------------------------------------------------
    // Pulsing animation for "CALL IN PROGRESS" indicator
    // -------------------------------------------------------------------------
    val infiniteTransition = rememberInfiniteTransition(label = "pay123_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_alpha",
    )

    GlassScaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))

            // -----------------------------------------------------------------
            // CALL IN PROGRESS animated indicator
            // -----------------------------------------------------------------
            Row(
                modifier = Modifier
                    .alpha(pulseAlpha)
                    .background(
                        color = colors.errorRed.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(50),
                    )
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "🔴",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "CALL IN PROGRESS",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = colors.errorRed,
                    ),
                )
            }

            Spacer(Modifier.height(24.dp))

            // -----------------------------------------------------------------
            // Merchant details (visible in CallInProgress state)
            // -----------------------------------------------------------------
            val merchantName: String
            val upiId: String
            val formattedAmount: String

            when (val s = uiState) {
                is Pay123ViewModel.UiState.CallInProgress -> {
                    merchantName = s.merchantName
                    upiId = s.upiId
                    formattedAmount = formatAmountPaise(s.amount)
                }
                is Pay123ViewModel.UiState.AwaitingConfirmation -> {
                    merchantName = s.merchantName
                    upiId = ""
                    formattedAmount = formatAmountPaise(s.amount)
                }
                else -> {
                    merchantName = ""
                    upiId = ""
                    formattedAmount = ""
                }
            }

            if (merchantName.isNotEmpty()) {
                MerchantAvatar(
                    merchantName = merchantName,
                    modifier = Modifier,
                    size = 64.dp,
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = merchantName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(4.dp))

                if (upiId.isNotEmpty()) {
                    Text(
                        text = "$upiId · $formattedAmount",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = colors.onSurfaceVariant,
                        ),
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Text(
                        text = formattedAmount,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = colors.onSurfaceVariant,
                        ),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // -----------------------------------------------------------------
            // IVR step list (shown while call is in progress)
            // -----------------------------------------------------------------
            if (uiState is Pay123ViewModel.UiState.CallInProgress ||
                uiState is Pay123ViewModel.UiState.InitiatingCall
            ) {
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    thickness = 1.dp,
                    color = colors.outline,
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "Follow IVR Steps",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = colors.onSurfaceVariant,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(16.dp))

                val ivrSteps = listOf(
                    "Listen to the IVR welcome menu",
                    "Select the option for UPI payment",
                    "Enter your 6-digit UPI PIN",
                    "Confirm the payment amount",
                )
                ivrSteps.forEachIndexed { index, step ->
                    IvrStepRow(number = index + 1, text = step)
                    Spacer(Modifier.height(12.dp))
                }

                Spacer(Modifier.height(16.dp))

                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    thickness = 1.dp,
                    color = colors.outline,
                )

                Spacer(Modifier.height(20.dp))

                // Warning banner: do not close screen
                WarningBanner()
            }

            Spacer(Modifier.height(24.dp))
        }

        // ---------------------------------------------------------------------
        // "Did your payment succeed?" dialog shown in AWAITING_CONFIRMATION
        // ---------------------------------------------------------------------
        if (uiState is Pay123ViewModel.UiState.AwaitingConfirmation) {
            val s = uiState as Pay123ViewModel.UiState.AwaitingConfirmation
            AlertDialog(
                onDismissRequest = { /* non-dismissible — user must choose */ },
                title = {
                    Text(
                        text = "Did your payment succeed?",
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                text = {
                    Text(
                        text = "Did your payment of ${formatAmountPaise(s.amount)} to ${s.merchantName} go through?",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = {
                    Button(
                        onClick = onConfirmSuccess,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LocalOfflinePayColors.current.successGreen,
                        ),
                    ) {
                        Text("✅  Yes, Succeeded")
                    }
                },
                dismissButton = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.End,
                    ) {
                        OutlinedButton(onClick = onConfirmFailure) {
                            Text("❌  No, Failed")
                        }
                        TextButton(onClick = onDismissWithoutConfirming) {
                            Text("Not sure")
                        }
                    }
                },
                shape = MaterialTheme.shapes.large,
            )
        }
    }
}

// ─── Private helpers ──────────────────────────────────────────────────────────

/**
 * Renders a single numbered IVR instruction row.
 *
 * @param number 1-based step number.
 * @param text   Instruction text for this step.
 */
@Composable
private fun IvrStepRow(number: Int, text: String) {
    val colors = LocalOfflinePayColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Step number badge
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(
                    color = colors.brandPrimaryContainer,
                    shape = RoundedCornerShape(50),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$number",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = colors.brandOnPrimaryContainer,
                ),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Warning card: "Do not close this screen during the call".
 */
@Composable
private fun WarningBanner() {
    val colors = LocalOfflinePayColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = colors.warningContainer,
                shape = MaterialTheme.shapes.medium,
            )
            .border(
                width = 1.dp,
                color = colors.warningAmber,
                shape = MaterialTheme.shapes.medium,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "⚠",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "Do not close this screen during the call",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium,
                color = colors.onWarningContainer,
            ),
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Formats an amount in paise (Long) to a human-readable Indian Rupee string.
 *
 * Examples:
 * - 50000L → "₹500.00"
 * - 100L   → "₹1.00"
 * - 0L     → "₹0.00"
 */
internal fun formatAmountPaise(amountPaise: Long): String {
    val rupees = amountPaise / 100.0
    return "₹%.2f".format(rupees)
}
