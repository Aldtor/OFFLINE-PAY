package com.offlinepay.feature.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.offlinepay.core.designsystem.OfflinePayBackground
import kotlinx.coroutines.flow.collectLatest

// ---------------------------------------------------------------------------
// OnboardingScreen — 4-step first-launch onboarding
//
// Steps:
//   0  Welcome
//   1  Offline Explanation
//   2  Permissions  (Camera, CALL_PHONE, READ_PHONE_STATE)
//   3  Completion / "Get Started"
//
// Design reference: Section 10.1 (route: "onboarding"), Section 10.3 (transitions)
// Requirements: Req 10.1–10.4
// ---------------------------------------------------------------------------

private const val TOTAL_STEPS = 4

/**
 * Root composable for the onboarding flow.
 *
 * @param onNavigateToDashboard Callback invoked when onboarding is complete.
 * @param viewModel             Hilt-injected [OnboardingViewModel].
 */
@Composable
fun OnboardingScreen(
    onNavigateToDashboard: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Collect one-shot navigation events
    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                OnboardingUiEvent.NavigateToDashboard -> onNavigateToDashboard()
            }
        }
    }

    OfflinePayBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            // ── Step content ──────────────────────────────────────────────────
            AnimatedContent(
                targetState = uiState.currentStep,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { it } + fadeIn()) togetherWith
                                (slideOutHorizontally { -it } + fadeOut())
                    } else {
                        (slideInHorizontally { -it } + fadeIn()) togetherWith
                                (slideOutHorizontally { it } + fadeOut())
                    }
                },
                modifier = Modifier.weight(1f),
                label = "onboarding_step",
            ) { step ->
                when (step) {
                    0 -> WelcomeStep()
                    1 -> OfflineExplanationStep()
                    2 -> PermissionsStep(uiState = uiState, onAction = viewModel::onAction)
                    else -> CompletionStep()
                }
            }

            // ── Step indicators (dots) ────────────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                repeat(TOTAL_STEPS) { index ->
                    val isActive = index == uiState.currentStep
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (isActive) 12.dp else 8.dp)
                            .background(
                                color = if (isActive) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                                shape = CircleShape,
                            ),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Primary action button ─────────────────────────────────────────
            Button(
                onClick = {
                    if (uiState.currentStep < TOTAL_STEPS - 1) {
                        viewModel.onAction(OnboardingUiAction.NextStep)
                    } else {
                        viewModel.onAction(OnboardingUiAction.CompleteOnboarding)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text(if (uiState.currentStep < TOTAL_STEPS - 1) "Continue" else "Get Started")
            }

            // Back button (hidden on first step)
            if (uiState.currentStep > 0) {
                OutlinedButton(
                    onClick = { viewModel.onAction(OnboardingUiAction.PreviousStep) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    Text("Back")
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Step composables
// ---------------------------------------------------------------------------

/** Step 0 — Welcome screen. */
@Composable
private fun WelcomeStep() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Welcome to OfflinePay",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Send and receive payments even without an internet connection.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

/** Step 1 — Explains how offline payments work. */
@Composable
private fun OfflineExplanationStep() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "How It Works",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "OfflinePay uses USSD and DTMF tones to process payments through your mobile network — no internet needed.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Scan a QR code to start a payment, and we'll handle the rest via your carrier.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Step 2 — Permission requests.
 *
 * Requests Camera, CALL_PHONE, and READ_PHONE_STATE at runtime.
 * If a permission is permanently denied, shows an "Open Settings" button
 * that deep-links to the app's system settings page.
 */
@Composable
private fun PermissionsStep(
    uiState: OnboardingUiState,
    onAction: (OnboardingUiAction) -> Unit,
) {
    val context = LocalContext.current

    // ── Permission launchers ──────────────────────────────────────────────
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        onAction(OnboardingUiAction.CameraPermissionResult(granted))
    }

    val phoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        onAction(OnboardingUiAction.PhonePermissionResult(granted))
    }

    val readPhoneStateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        onAction(OnboardingUiAction.ReadPhoneStatePermissionResult(granted))
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Permissions Needed",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "OfflinePay needs the following permissions to function.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))

        // Camera permission
        PermissionRow(
            title = "Camera",
            description = "Required to scan UPI QR codes.",
            isGranted = uiState.isCameraGranted,
            onRequest = { cameraLauncher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = { openAppSettings(context) },
        )

        Spacer(Modifier.height(16.dp))

        // CALL_PHONE permission
        PermissionRow(
            title = "Make Calls",
            description = "Required to initiate USSD payment sessions.",
            isGranted = uiState.isPhoneGranted,
            onRequest = { phoneLauncher.launch(Manifest.permission.CALL_PHONE) },
            onOpenSettings = { openAppSettings(context) },
        )

        Spacer(Modifier.height(16.dp))

        // READ_PHONE_STATE permission
        PermissionRow(
            title = "Read Phone State",
            description = "Required to detect SIM cards and select the right carrier.",
            isGranted = uiState.isReadPhoneStateGranted,
            onRequest = { readPhoneStateLauncher.launch(Manifest.permission.READ_PHONE_STATE) },
            onOpenSettings = { openAppSettings(context) },
        )
    }
}

/** Step 3 — Completion / "Get Started". */
@Composable
private fun CompletionStep() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "You're All Set!",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Tap \"Get Started\" to begin making offline payments.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

// ---------------------------------------------------------------------------
// Helper composable — single permission row
// ---------------------------------------------------------------------------

/**
 * Displays a permission item with its status.
 *
 * - If granted: shows a green "Granted" label.
 * - If not yet granted: shows a "Grant" button to request the permission.
 *
 * The [onOpenSettings] callback is available for the host to redirect to system
 * settings when the permission is permanently denied. Because the standard
 * [rememberLauncherForActivityResult] API doesn't expose "permanently denied"
 * status directly, the "Open Settings" fallback is surfaced as a secondary
 * action here so users can always reach it if needed.
 */
@Composable
private fun PermissionRow(
    title: String,
    description: String,
    isGranted: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (isGranted) {
            Text(
                text = "Granted",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp),
            )
        } else {
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Button(onClick = onRequest) {
                    Text("Grant")
                }
                OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text("Open Settings")
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Helper — open app system settings page
// ---------------------------------------------------------------------------

/**
 * Opens the system settings page for this app.
 * Used when a permission is permanently denied and must be granted via Settings.
 *
 * Requirements: Req 10.3 (permanent denial settings redirect)
 */
private fun openAppSettings(context: android.content.Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
