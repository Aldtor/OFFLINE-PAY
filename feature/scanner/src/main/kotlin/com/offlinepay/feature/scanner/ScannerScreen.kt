package com.offlinepay.feature.scanner

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinepay.core.designsystem.GlassCard
import com.offlinepay.core.designsystem.OfflinePayBackground
import com.offlinepay.core.designsystem.components.ScannerOverlay
import com.offlinepay.core.domain.model.PaymentParams
import com.offlinepay.feature.scanner.camera.BarcodeAnalyzer
import com.offlinepay.feature.scanner.camera.CameraPreview
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ScannerScreen(
    onNavigateToPaymentConfirmation: (PaymentParams) -> Unit,
    viewModel: ScannerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalContext.current
    val activity = context as? Activity

    // FLAG_SECURE — required on scanner screen per Req 9.23
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    // ── Camera runtime permission ────────────────────────────────────────────
    // Without CAMERA granted, CameraX cannot open the device camera and the
    // PreviewView renders as a black screen. We must request the permission
    // before binding the camera and only show the preview once it is granted.
    fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    var permissionGranted by remember { mutableStateOf(hasCameraPermission()) }
    // Tracks whether we've asked at least once, so we can distinguish
    // "not asked yet" from "denied" and surface the right call-to-action.
    var permissionRequested by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
        permissionRequested = true
        viewModel.onAction(
            if (granted) ScannerUiAction.PermissionGranted else ScannerUiAction.PermissionDenied
        )
    }

    // Re-check on every resume (user may grant from the system Settings screen and return).
    LaunchedEffect(Unit) {
        if (!permissionGranted) {
            if (!permissionRequested) {
                permissionRequested = true
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        } else {
            viewModel.onAction(ScannerUiAction.PermissionGranted)
        }
    }

    // Collect one-shot navigation events
    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is ScannerUiEvent.NavigateToPaymentConfirmation -> {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onNavigateToPaymentConfirmation(event.paymentParams)
                }
            }
        }
    }

    // BarcodeAnalyzer retained across recompositions
    val analyzer = remember {
        BarcodeAnalyzer(
            onQrDecoded = { raw -> viewModel.onAction(ScannerUiAction.FrameAnalysed(raw)) },
            onNoDetection = {},
        )
    }
    // Resume analyzer when state returns to Ready after ParseError
    LaunchedEffect(uiState) {
        if (uiState is ScannerUiState.Ready) analyzer.resume()
    }
    // Cleanup on leave
    DisposableEffect(Unit) {
        onDispose { analyzer.close() }
    }

    if (!permissionGranted) {
        CameraPermissionRationale(
            // If we've already asked and it's still not granted, the system dialog
            // won't show again — send the user to app settings instead.
            alreadyDenied = permissionRequested,
            onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                )
                context.startActivity(intent)
            },
        )
        return
    }

    Box(Modifier.fillMaxSize()) {
        // Camera preview — feeds frames into BarcodeAnalyzer
        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            onFrameAnalysed = { imageProxy -> analyzer.analyze(imageProxy) },
        )

        // Scanner overlay reticle
        ScannerOverlay(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        )

        // Torch FAB
        val ready = uiState as? ScannerUiState.Ready
        FloatingActionButton(
            onClick = { viewModel.onAction(ScannerUiAction.ToggleTorch) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
        ) {
            Icon(
                imageVector = if (ready?.isTorchOn == true) Icons.Filled.FlashOff else Icons.Filled.FlashOn,
                contentDescription = if (ready?.isTorchOn == true) "Turn off torch" else "Turn on torch",
            )
        }

        // Parse error banner
        AnimatedVisibility(
            visible = uiState is ScannerUiState.ParseError,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp),
        ) {
            val error = uiState as? ScannerUiState.ParseError
            if (error != null) {
                GlassCard(elevated = true) {
                    Column(Modifier.padding(16.dp)) {
                        Text(text = error.detectedType ?: "Non-UPI QR Code", style = MaterialTheme.typography.titleSmall)
                        Text(text = error.message, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { viewModel.onAction(ScannerUiAction.RescanRequested) }) {
                            Text("Rescan")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Full-screen rationale shown when the CAMERA permission has not been granted.
 * Offers to (re)request the permission, or — once the system dialog will no longer
 * appear (permanently denied) — to open the app's settings page.
 */
@Composable
private fun CameraPermissionRationale(
    alreadyDenied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    OfflinePayBackground {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            GlassCard(
                modifier = Modifier.padding(24.dp),
                elevated = true,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Camera access needed",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "OfflinePay uses the camera to scan payment QR codes. " +
                            "No images leave your device.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(24.dp))
                    if (alreadyDenied) {
                        Button(onClick = onOpenSettings) { Text("Open settings") }
                    } else {
                        Button(onClick = onRequest) { Text("Allow camera") }
                    }
                }
            }
        }
    }
}
