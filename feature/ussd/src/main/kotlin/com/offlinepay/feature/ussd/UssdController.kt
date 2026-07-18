package com.offlinepay.feature.ussd

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import com.offlinepay.core.domain.model.PaymentMethodType
import com.offlinepay.core.domain.model.PaymentParams
import com.offlinepay.core.domain.model.TransactionRecord
import com.offlinepay.core.domain.model.TransactionStatus
import com.offlinepay.core.domain.usecase.transaction.SaveTransactionUseCase
import com.offlinepay.feature.ussd.autodrive.UssdAutoDriveSession
import dagger.hilt.android.qualifiers.ApplicationContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Launches an interactive `*99#` USSD session on the selected SIM and arms
 * [UssdAutoDriveSession] so [com.offlinepay.feature.ussd.autodrive.UssdAccessibilityService]
 * can auto-fill the menus (payee UPI ID + amount), leaving only the UPI PIN to the user.
 *
 * Why `ACTION_CALL` and not `TelephonyManager.sendUssdRequest`: `sendUssdRequest` is a
 * single request→response API that **cannot reply** to the interactive `*99#` menu chain,
 * so it can never complete a send-money flow. Dialling `*99#` as a call surfaces the
 * system USSD dialogs, which an AccessibilityService can read and drive.
 *
 * A [TransactionStatus.PENDING] [TransactionRecord] is persisted **before** dialling so the
 * attempt survives process death.
 *
 * Design reference: Section 5.2 (UssdController)
 * Requirements: Req 5.1 (USSD initiation), Req 5.2 (permission fallback),
 *               Req 8.1 (persist every attempt as PENDING)
 */
@Singleton
class UssdController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val saveTransactionUseCase: SaveTransactionUseCase,
    private val autoDriveSession: UssdAutoDriveSession,
) {

    /**
     * Persists a PENDING record, arms the auto-drive session with the payee/amount, and
     * dials `*99#` on the SIM identified by [subscriptionId].
     *
     * @return the generated transaction ID.
     */
    suspend fun initiateUssd(
        params: PaymentParams,
        subscriptionId: Int,
    ): String {
        // Persist PENDING record before dispatching so it survives process death.
        val now = System.currentTimeMillis()
        val transactionId = UUID.randomUUID().toString()
        val record = TransactionRecord(
            id                   = transactionId,
            timestampMs          = now,
            payeeUpiId           = params.upiId,
            payeeName            = params.payeeName,
            merchantCategoryCode = params.merchantCode,
            amountPaise          = params.amount ?: 0L,
            paymentMethod        = PaymentMethodType.USSD,
            status               = TransactionStatus.PENDING,
            transactionReference = params.transactionRef,
            createdAt            = now,
            updatedAt            = now,
        )
        saveTransactionUseCase(record)

        // Arm the accessibility auto-driver with what to type into the *99# menus.
        autoDriveSession.arm(
            payeeUpiId = params.upiId,
            amountRupees = paiseToRupees(params.amount ?: 0L),
            remark = params.note.orEmpty(),
        )

        dialUssd(subscriptionId)
        return transactionId
    }

    /**
     * Dials `*99#` via [Intent.ACTION_CALL] on the chosen subscription. Falls back to
     * [Intent.ACTION_DIAL] (pre-filled dialer) if `CALL_PHONE` is denied — the user then
     * presses call, and the auto-driver still takes over once the USSD dialog appears.
     */
    private fun dialUssd(subscriptionId: Int) {
        val ussdUri = Uri.fromParts("tel", USSD_ROOT, null) // encodes '#' → tel:*99%23
        val callIntent = Intent(Intent.ACTION_CALL, ussdUri).apply {
            phoneAccountHandleFor(subscriptionId)?.let {
                putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, it)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(callIntent)
        } catch (_: SecurityException) {
            // CALL_PHONE denied — open the dialer pre-filled instead.
            fallbackToDialIntent(ussdUri)
        } catch (_: ActivityNotFoundException) {
            fallbackToDialIntent(ussdUri)
        }
    }

    private fun fallbackToDialIntent(ussdUri: Uri) {
        val intent = Intent(Intent.ACTION_DIAL, ussdUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            autoDriveSession.markFailed("No dialer available")
        }
    }

    /**
     * Resolves the [PhoneAccountHandle] for [subscriptionId] so the USSD call is placed on
     * the correct SIM on dual-SIM devices. Returns null (→ default SIM) when it cannot be
     * resolved or `READ_PHONE_STATE` is denied.
     */
    private fun phoneAccountHandleFor(subscriptionId: Int): PhoneAccountHandle? {
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) return null
        return try {
            val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                ?: return null
            @Suppress("MissingPermission")
            val handles = telecom.callCapablePhoneAccounts
            if (handles.isEmpty()) return null
            // Most OEMs use the subscription id (or its slot index) as the handle id.
            handles.firstOrNull { it.id == subscriptionId.toString() }
                ?: handles.getOrNull(slotIndexFor(subscriptionId))
        } catch (_: SecurityException) {
            null
        }
    }

    private fun slotIndexFor(subscriptionId: Int): Int {
        return try {
            val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                as? SubscriptionManager ?: return 0
            @Suppress("MissingPermission")
            sm.getActiveSubscriptionInfo(subscriptionId)?.simSlotIndex ?: 0
        } catch (_: SecurityException) {
            0
        }
    }

    /** Converts paise to a plain rupee string ("10000" → "100", "10050" → "100.50"). */
    private fun paiseToRupees(paise: Long): String {
        val rupees = BigDecimal(paise).divide(BigDecimal(100)).setScale(2, RoundingMode.HALF_UP)
        return if (rupees.stripTrailingZeros().scale() <= 0) {
            rupees.toLong().toString()
        } else {
            rupees.stripTrailingZeros().toPlainString()
        }
    }

    companion object {
        private const val USSD_ROOT = "*99#"
    }
}
