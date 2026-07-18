package com.offlinepay.core.security.clipboard

import android.content.ClipboardManager
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * WorkManager one-time task that clears the clipboard 60 seconds after a sensitive
 * payment field (UPI ID) is copied.
 *
 * The worker checks if the clipboard still contains the sensitive value before
 * clearing — if the user has copied something else in the meantime, the clipboard
 * is left untouched.
 *
 * No WakeLock is acquired (Req 16.8).
 *
 * Design reference: Section 8.5 (Clipboard Security)
 * Requirements: Req 9.25, Req 9.26
 */
@HiltWorker
class ClipboardClearWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val sensitiveValue = inputData.getString(KEY_SENSITIVE_VALUE) ?: return Result.success()

        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val currentClip = clipboardManager.primaryClip

        // Only clear if the clipboard still contains the sensitive value
        if (currentClip != null && currentClip.itemCount > 0) {
            val clipText = currentClip.getItemAt(0).text?.toString()
            if (clipText == sensitiveValue) {
                clipboardManager.setPrimaryClip(
                    android.content.ClipData.newPlainText("", ""),
                )
            }
        }

        return Result.success()
    }

    companion object {
        const val KEY_SENSITIVE_VALUE = "sensitive_value"
        private const val DELAY_SECONDS = 60L

        /**
         * Schedules a clipboard clear task 60 seconds from now.
         *
         * @param context Application context.
         * @param sensitiveValue The exact value that was copied. The worker will only
         *                       clear the clipboard if this value is still present.
         */
        fun schedule(context: Context, sensitiveValue: String) {
            val request = OneTimeWorkRequestBuilder<ClipboardClearWorker>()
                .setInitialDelay(DELAY_SECONDS, TimeUnit.SECONDS)
                .setInputData(
                    workDataOf(KEY_SENSITIVE_VALUE to sensitiveValue),
                )
                .addTag("clipboard_clear")
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
