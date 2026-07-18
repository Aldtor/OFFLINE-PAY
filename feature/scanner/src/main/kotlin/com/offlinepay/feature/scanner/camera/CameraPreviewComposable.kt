package com.offlinepay.feature.scanner.camera

import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Composable that renders a live camera preview backed by CameraX and drives frame analysis
 * via [onFrameAnalysed].
 *
 * Camera binding uses [ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST] to ensure the analyzer always
 * receives the most recent frame and never falls behind.
 *
 * Resource cleanup:
 * - CameraX automatically unbinds use cases when the [LocalLifecycleOwner] reaches the
 *   DESTROYED state. An explicit [DisposableEffect] is added so callers can also close ML Kit
 *   resources (e.g. [BarcodeAnalyzer.close]) in the same [onDispose] block if they wrap this
 *   composable.
 * - Per Req 15.2 and design Section 15.2, the camera provider is unbound in [onDispose] as an
 *   extra safety net.
 *
 * @param modifier Modifier applied to the [AndroidView] surface.
 * @param torchEnabled When `true`, the rear flash unit is turned on (used by the torch
 *   FAB). Applied both on initial bind and whenever the value changes.
 * @param onFrameAnalysed Callback invoked for each camera frame. The callee is responsible for
 *   calling [ImageProxy.close] after processing.
 *
 * Requirements: Req 2.1, Req 2.6, Req 2.8
 * Design reference: Section 5.1 (Scanner Pipeline), Section 15.2 (Resource cleanup)
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    torchEnabled: Boolean = false,
    onFrameAnalysed: (ImageProxy) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // Retain the cameraProvider reference so we can unbind on dispose.
    val cameraProviderHolder = remember { mutableListOf<ProcessCameraProvider>() }
    // Retain the bound Camera so the torch can be toggled after binding.
    val cameraHolder = remember { mutableListOf<Camera>() }

    // Explicit lifecycle-scoped cleanup for ML Kit scanner close and camera unbind.
    // CameraX will also unbind automatically on lifecycle DESTROYED, but this gives callers
    // a deterministic hook (design Section 15.2).
    DisposableEffect(lifecycleOwner) {
        onDispose {
            cameraProviderHolder.firstOrNull()?.unbindAll()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener(
                {
                    val cameraProvider = cameraProviderFuture.get()

                    // Store for cleanup in DisposableEffect.
                    cameraProviderHolder.clear()
                    cameraProviderHolder.add(cameraProvider)

                    val preview = Preview.Builder()
                        .build()
                        .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                                onFrameAnalysed(imageProxy)
                            }
                        }

                    try {
                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis,
                        )
                        // Store the Camera and apply the current torch state on bind.
                        cameraHolder.clear()
                        cameraHolder.add(camera)
                        if (camera.cameraInfo.hasFlashUnit()) {
                            camera.cameraControl.enableTorch(torchEnabled)
                        }
                    } catch (e: Exception) {
                        // Camera bind failed — logged here for debug; ViewModel can emit error state.
                        android.util.Log.e("CameraPreview", "Failed to bind camera use cases", e)
                    }
                },
                ContextCompat.getMainExecutor(ctx),
            )

            previewView
        },
        // Runs on every recomposition — reflects torch toggles onto the hardware
        // once the camera has finished binding (holder is empty until then).
        update = {
            cameraHolder.firstOrNull()?.let { camera ->
                if (camera.cameraInfo.hasFlashUnit()) {
                    camera.cameraControl.enableTorch(torchEnabled)
                }
            }
        },
    )
}
