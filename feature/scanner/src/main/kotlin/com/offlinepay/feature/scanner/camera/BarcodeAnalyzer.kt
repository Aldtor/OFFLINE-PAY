package com.offlinepay.feature.scanner.camera

import android.graphics.Bitmap
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BinaryBitmap
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.common.HybridBinarizer

/**
 * [ImageAnalysis.Analyzer] that decodes QR codes from camera frames.
 *
 * Decoding strategy:
 * 1. **Primary — ML Kit**: processes every frame via [BarcodeScanner] configured for
 *    [Barcode.FORMAT_QR_CODE].
 * 2. **Fallback — ZXing**: after [ML_KIT_MISS_THRESHOLD] (3) consecutive ML Kit
 *    non-detections, a ZXing [MultiFormatReader] is attempted on the same frame.
 *    On the next successful ML Kit decode, the miss counter resets.
 *
 * Deduplication:
 * - [isPaused] is set to `true` immediately after a successful decode to avoid firing
 *   [onQrDecoded] multiple times for the same code.
 * - Call [resume] (e.g. when the user taps "Rescan") to re-enable analysis.
 *
 * Resource cleanup:
 * - Call [close] to release the ML Kit [BarcodeScanner] client. ZXing holds no
 *   long-lived native resources beyond the [MultiFormatReader] instance.
 *
 * Requirements: Req 2.1, Req 2.2
 * Design reference: Section 5.1 (Scanner Pipeline — primary/fallback decoders)
 */
class BarcodeAnalyzer(
    /** Invoked on the main thread with the raw QR string when a code is decoded. */
    private val onQrDecoded: (String) -> Unit,
    /** Invoked on the main thread when a frame produced no detection. */
    private val onNoDetection: () -> Unit,
) : ImageAnalysis.Analyzer {

    // -------------------------------------------------------------------------
    // Primary decoder — ML Kit
    // -------------------------------------------------------------------------

    private val mlKitScanner: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build(),
    )

    // -------------------------------------------------------------------------
    // Fallback decoder — ZXing
    // -------------------------------------------------------------------------

    private val zxingReader = MultiFormatReader()

    /** Number of consecutive frames where ML Kit produced no result. */
    private var consecutiveMlKitMisses = 0

    /** Trigger ZXing fallback after this many consecutive ML Kit non-detections (Req 2.2). */
    private val ML_KIT_MISS_THRESHOLD = 3

    // -------------------------------------------------------------------------
    // Deduplication
    // -------------------------------------------------------------------------

    /**
     * When `true` incoming frames are dropped immediately after [ImageProxy.close].
     * Set to `true` on first successful decode; reset to `false` by [resume].
     */
    @Volatile
    var isPaused: Boolean = false

    // -------------------------------------------------------------------------
    // Analyzer implementation
    // -------------------------------------------------------------------------

    override fun analyze(image: ImageProxy) {
        if (isPaused) {
            image.close()
            return
        }

        val mediaImage = image.image
        if (mediaImage == null) {
            image.close()
            onNoDetection()
            return
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)

        mlKitScanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                // A previously in-flight frame may resolve after a successful decode
                // has already paused the analyzer — drop it so we never fire
                // onQrDecoded (and therefore navigate) twice for one scan.
                if (isPaused) {
                    image.close()
                    return@addOnSuccessListener
                }
                val rawValue = barcodes.firstOrNull { it.rawValue != null }?.rawValue
                if (rawValue != null) {
                    consecutiveMlKitMisses = 0
                    isPaused = true // pause until user taps "Rescan"
                    image.close()
                    onQrDecoded(rawValue)
                } else {
                    consecutiveMlKitMisses++
                    if (consecutiveMlKitMisses >= ML_KIT_MISS_THRESHOLD) {
                        // ML Kit missed enough times — try ZXing before closing.
                        tryZxingFallback(image)
                    } else {
                        image.close()
                        onNoDetection()
                    }
                }
            }
            .addOnFailureListener {
                image.close()
                onNoDetection()
            }
    }

    // -------------------------------------------------------------------------
    // ZXing fallback
    // -------------------------------------------------------------------------

    /**
     * Attempts to decode [image] using ZXing [MultiFormatReader].
     *
     * [ImageProxy.close] is called in a `finally` so the frame is ALWAYS released —
     * even if [ImageProxy.toBitmap] itself throws. With
     * [ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST] an unclosed proxy stalls the whole
     * feed (no further frames are delivered), which would freeze the camera preview.
     */
    private fun tryZxingFallback(image: ImageProxy) {
        try {
            val bitmap: Bitmap = image.toBitmap()
            val binaryBitmap = BinaryBitmap(HybridBinarizer(BitmapLuminanceSource(bitmap)))
            val result = zxingReader.decodeWithState(binaryBitmap)
            consecutiveMlKitMisses = 0
            isPaused = true
            onQrDecoded(result.text)
        } catch (_: Exception) {
            // NotFoundException or any other ZXing failure — no QR in this frame.
            onNoDetection()
        } finally {
            zxingReader.reset()
            image.close()
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /** Releases the ML Kit [BarcodeScanner] client. Call from [DisposableEffect.onDispose]. */
    fun close() {
        mlKitScanner.close()
    }

    /** Re-enables frame analysis after a successful decode (e.g. user taps "Rescan"). */
    fun resume() {
        isPaused = false
    }
}

// =============================================================================
// BitmapLuminanceSource — ZXing helper
// =============================================================================

/**
 * ZXing [LuminanceSource] backed by an Android [Bitmap].
 *
 * Converts each ARGB pixel to approximate luminance using an integer average of
 * the R, G, and B channels (fast approximation; sufficient for QR decoding).
 *
 * This avoids the dependency on the ZXing Android client library
 * (`com.google.zxing:android-client`) which is not included in the project
 * version catalog. Only `com.google.zxing:core` is required.
 */
class BitmapLuminanceSource(private val bitmap: Bitmap) :
    LuminanceSource(bitmap.width, bitmap.height) {

    override fun getMatrix(): ByteArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return ByteArray(pixels.size) { i ->
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            ((r + g + b) / 3).toByte()
        }
    }

    override fun getRow(y: Int, row: ByteArray?): ByteArray {
        val result = if (row != null && row.size >= width) row else ByteArray(width)
        val pixels = IntArray(width)
        bitmap.getPixels(pixels, 0, width, 0, y, width, 1)
        pixels.forEachIndexed { i, pixel ->
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            result[i] = ((r + g + b) / 3).toByte()
        }
        return result
    }
}
