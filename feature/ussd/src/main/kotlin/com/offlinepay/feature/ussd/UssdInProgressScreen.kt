package com.offlinepay.feature.ussd

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinepay.core.designsystem.GlassScaffold

/**
 * Full-screen composable displayed while a USSD payment session is in progress.
 *
 * Features:
 * - Pulsing alpha animation (0.4 → 1.0 → 0.4, 800 ms infinite) on the status icon area.
 * - Session status text derived from [UssdViewModel.UiState].
 * - 30-second countdown indicator ([CircularProgressIndicator] with remaining seconds text).
 * - Cancel button that calls [UssdViewModel.cancel].
 * - Fallback offer dialog shown when the state is [UssdViewModel.UiState.Failed] or
 *   [UssdViewModel.UiState.Timeout], or when a [UssdViewModel.UiEvent.OfferFallbackTo123Pay]
 *   event is received.
 * - [WindowManager.LayoutParams.FLAG_SECURE] applied while the screen is shown, preventing
 *   screenshots and screen recording during sensitive payment operations.
 *
 * Design reference: Section 5.6 (UssdInProgressScreen)
 * Requirements: Req 5.4 (countdown), Req 5.7 (timeout / fallback dialog), Req 5.10 (FLAG_SECURE)
 *
 * @param onFallbackAccepted   Called when the user accepts the 123PAY fallback offer.
 * @param onFallbackDismissed  Called when the user dismisses the fallback offer dialog.
 * @param onNavigateUp         Called when the session is cancelled or completed.
 * @param viewModel            The backing [UssdViewModel] (injected by Hilt by default).
 */
@Composable
fun UssdInProgressScreen(
    onFallbackAccepted: () -> Unit = {},
    onFallbackDismissed: () -> Unit = {},
    onNavigateUp: () -> Unit = {},
    viewModel: UssdViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showFallbackDialog by remember { mutableStateOf(false) }

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
    // Collect one-shot events
    // -------------------------------------------------------------------------
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UssdViewModel.UiEvent.OfferFallbackTo123Pay    -> showFallbackDialog = true
                is UssdViewModel.UiEvent.NavigateToSuccess        -> onNavigateUp()
            }
        }
    }

    // Also show dialog on FAILED / TIMEOUT states (belt-and-suspenders)
    LaunchedEffect(uiState) {
        if (uiState is UssdViewModel.UiState.Failed || uiState is UssdViewModel.UiState.Timeout) {
            showFallbackDialog = true
        }
        if (uiState is UssdViewModel.UiState.Cancelled) {
            onNavigateUp()
        }
    }

    // -------------------------------------------------------------------------
    // Pulsing animation
    // -------------------------------------------------------------------------
    val infiniteTransition = rememberInfiniteTransition(label = "ussd_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue   = 0.4f,
        targetValue    = 1.0f,
        animationSpec  = infiniteRepeatable(
            animation  = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse,
        ),
        label          = "pulse_alpha",
    )

    // -------------------------------------------------------------------------
    // Countdown value from state
    // -------------------------------------------------------------------------
    val countdownSeconds = when (val s = uiState) {
        is UssdViewModel.UiState.Requesting -> s.countdownSeconds
        is UssdViewModel.UiState.Active     -> s.countdownSeconds
        else                                -> 30
    }

    val statusText = when (val s = uiState) {
        is UssdViewModel.UiState.Idle       -> "Initialising…"
        is UssdViewModel.UiState.Requesting -> "Connecting to bank…"
        is UssdViewModel.UiState.Active     -> s.statusText
        is UssdViewModel.UiState.AwaitingPin -> "Enter your UPI PIN"
        is UssdViewModel.UiState.Completed  -> "Payment complete"
        is UssdViewModel.UiState.Failed     -> "Payment failed"
        is UssdViewModel.UiState.Timeout    -> "Request timed out"
        is UssdViewModel.UiState.Cancelled  -> "Cancelled"
    }

    val isAwaitingPin = uiState is UssdViewModel.UiState.AwaitingPin

    // -------------------------------------------------------------------------
    // UI
    // -------------------------------------------------------------------------
    GlassScaffold { innerPadding ->
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Pulsing status indicator
            Box(
                modifier        = Modifier
                    .size(80.dp)
                    .alpha(pulseAlpha),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier  = Modifier.fillMaxSize(),
                    progress  = { countdownSeconds / 30f },
                    color     = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Text(
                    text  = "$countdownSeconds",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text       = statusText,
                style      = if (isAwaitingPin) MaterialTheme.typography.headlineSmall
                             else MaterialTheme.typography.bodyLarge,
                textAlign  = TextAlign.Center,
            )

            if (isAwaitingPin) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text      = "The payment details are filled in. Type your UPI PIN in the " +
                        "phone's dialog to finish. We never see or enter your PIN.",
                    style     = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (!viewModel.isAutoPayEnabled) {
                // Auto-drive is off: menus won't be filled automatically. Offer to enable it.
                Spacer(Modifier.height(12.dp))
                Text(
                    text      = "Auto-pay is off, so you'll navigate the *99# menu yourself. " +
                        "Turn on OfflinePay Auto-Pay to auto-fill the payee and amount next time.",
                    style     = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = { viewModel.openAutoPaySettings() }) {
                    Text("Enable Auto-Pay")
                }
            }

            Spacer(Modifier.height(32.dp))

            OutlinedButton(onClick = { viewModel.cancel() }) {
                Text("Cancel")
            }
        }

        // -------------------------------------------------------------------------
        // Fallback offer dialog
        // -------------------------------------------------------------------------
        if (showFallbackDialog) {
            AlertDialog(
                onDismissRequest = {
                    showFallbackDialog = false
                    onFallbackDismissed()
                },
                title            = { Text("Switch to 123PAY?") },
                text             = {
                    Text(
                        "USSD payment did not complete. You can try paying via 123PAY " +
                            "(works without internet or a data connection).",
                    )
                },
                confirmButton    = {
                    Button(onClick = {
                        showFallbackDialog = false
                        onFallbackAccepted()
                    }) {
                        Text("Try 123PAY")
                    }
                },
                dismissButton    = {
                    TextButton(onClick = {
                        showFallbackDialog = false
                        onFallbackDismissed()
                    }) {
                        Text("Dismiss")
                    }
                },
            )
        }
    }
}
