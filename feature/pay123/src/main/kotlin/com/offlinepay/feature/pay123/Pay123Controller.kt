package com.offlinepay.feature.pay123

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.offlinepay.core.common.dispatchers.AppDispatchers
import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.domain.error.DomainError
import com.offlinepay.core.domain.model.PaymentMethodType
import com.offlinepay.core.domain.model.PaymentParams
import com.offlinepay.core.domain.model.SimInfo
import com.offlinepay.core.domain.model.TransactionRecord
import com.offlinepay.core.domain.model.TransactionStatus
import com.offlinepay.core.domain.usecase.transaction.SaveTransactionUseCase
import com.offlinepay.core.telephony.VoiceServiceChecker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Initiates a UPI 123PAY IVR session by:
 *  1. Running a voice-service pre-flight check ([VoiceServiceChecker]).
 *  2. Persisting a [TransactionRecord] with [TransactionStatus.PENDING] **before**
 *     the call is launched so the record survives process death.
 *  3. Launching the IVR call via [Intent.ACTION_CALL] to the NPCI 123PAY IVR number.
 *
 * ### isCallInitiated flag
 * After `ACTION_CALL` is dispatched successfully, [isCallInitiated] is set to `true`.
 * This prevents the confirmation prompt from appearing before the IVR call has
 * actually been placed (e.g., on first compose of the guidance screen).
 *
 * ### Error handling
 * - [ActivityNotFoundException] → [DomainError.Pay123Error.CallFailed]
 * - [SecurityException] → [DomainError.Pay123Error.CallFailed] (CALL_PHONE denied)
 * - No cellular voice service → [DomainError.Pay123Error.NoService]
 *
 * Design reference: Section 7.1 (IVR flow design), Section 7.4 (failure modes)
 * Requirements: Req 6.1 (ACTION_CALL intent), Req 6.2 (guidance screen context)
 *               Req 6.5 (no programmatic call interception), Req 6.6 (persist PENDING)
 */
@Singleton
class Pay123Controller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val voiceServiceChecker: VoiceServiceChecker,
    private val saveTransactionUseCase: SaveTransactionUseCase,
    @Suppress("UNUSED_PARAMETER") private val dispatchers: AppDispatchers,
) {

    companion object {
        /**
         * NPCI UPI 123PAY IVR contact number (pan-India pre-defined IVR line).
         * Source: NPCI UPI 123PAY — official pre-defined IVR numbers are
         * 08045163666, 080 4516 3581, and 6366 200 200. We use the primary
         * 08045163666 line.
         *
         * (The previous value "9311382882" was not an NPCI number — it caused every
         * 123PAY payment to dial an unrelated mobile that "cannot receive calls".)
         */
        private const val NPCI_123PAY_NUMBER = "08045163666"
    }

    /**
     * True once `ACTION_CALL` has been launched in the current session.
     * Prevents the confirmation prompt from showing prematurely.
     *
     * Reset by [reset].
     */
    var isCallInitiated: Boolean = false
        private set

    /**
     * The transaction ID assigned to the PENDING record persisted before the call.
     * Exposed so the ViewModel can later update the status on user confirmation.
     */
    var pendingTransactionId: String? = null
        private set

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Runs pre-flight checks, persists a PENDING transaction record, and
     * launches the 123PAY IVR call.
     *
     * @param params  Payment parameters (UPI ID, amount, payee name, etc.).
     * @param simInfo The SIM to use for the IVR call (voice-service check).
     * @return [AppResult.Success] with the generated [transactionId] on success.
     *         [AppResult.Failure] with [DomainError.Pay123Error] on any failure.
     */
    suspend fun initiate(
        params: PaymentParams,
        simInfo: SimInfo,
    ): AppResult<String, DomainError.Pay123Error> {

        // 1. Pre-flight: verify voice service is available
        if (!voiceServiceChecker.isVoiceServiceAvailable(simInfo.subscriptionId)) {
            return AppResult.Failure(DomainError.Pay123Error.NoService)
        }

        // 2. Persist PENDING record before launching call
        val now = System.currentTimeMillis()
        val transactionId = UUID.randomUUID().toString()
        val record = TransactionRecord(
            id                   = transactionId,
            timestampMs          = now,
            payeeUpiId           = params.upiId,
            payeeName            = params.payeeName,
            merchantCategoryCode = params.merchantCode,
            amountPaise          = params.amount ?: 0L,
            paymentMethod        = PaymentMethodType.PAY123,
            status               = TransactionStatus.PENDING,
            operatorUsed         = simInfo.operatorType,
            simSlotUsed          = simInfo.slotIndex,
            transactionReference = params.transactionRef,
            createdAt            = now,
            updatedAt            = now,
        )

        when (val saveResult = saveTransactionUseCase(record)) {
            is AppResult.Failure -> {
                // Propagate as a CallFailed error — we must not proceed without a DB record
                return AppResult.Failure(
                    DomainError.Pay123Error.CallFailed(
                        "Failed to persist transaction: ${saveResult.error}",
                    ),
                )
            }
            else -> { /* Success — continue */ }
        }

        pendingTransactionId = transactionId

        // 3. Launch ACTION_CALL intent
        val callIntent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$NPCI_123PAY_NUMBER")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(callIntent)
            isCallInitiated = true
            AppResult.Success(transactionId)
        } catch (e: ActivityNotFoundException) {
            AppResult.Failure(
                DomainError.Pay123Error.CallFailed(
                    "ActivityNotFoundException: ${e.message}",
                ),
            )
        } catch (e: SecurityException) {
            AppResult.Failure(
                DomainError.Pay123Error.CallFailed(
                    "SecurityException (CALL_PHONE denied): ${e.message}",
                ),
            )
        }
    }

    /**
     * Resets the controller state so a new session can be started.
     * Called by [Pay123ViewModel] after a terminal state is processed.
     */
    fun reset() {
        isCallInitiated = false
        pendingTransactionId = null
    }
}
