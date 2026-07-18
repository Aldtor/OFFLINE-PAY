package com.offlinepay.core.telephony

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.domain.error.DomainError
import com.offlinepay.core.domain.model.SimInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects active SIM subscriptions on the device using [SubscriptionManager].
 *
 * Reads all active subscriptions and builds a list of [SimInfo] objects containing
 * subscription ID, resolved operator type, slot index, and voice service availability.
 *
 * Gracefully handles [READ_PHONE_STATE] permission denial by returning
 * [DomainError.PermissionError.ReadPhoneStateRequired].
 *
 * Design reference: Section 3.9 (`:core:telephony` — SimDetector)
 * Requirements: Req 3.1 (SubscriptionManager detection), Req 3.2 (operator identification),
 *               Req 3.4 (READ_PHONE_STATE handling), Req 3.5 (no SIM error)
 */
@Singleton
class SimDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val operatorResolver: OperatorResolver,
    private val voiceServiceChecker: VoiceServiceChecker,
) {

    /**
     * Returns the list of active [SimInfo] objects for all subscriptions on the device.
     *
     * @return [AppResult.Success] with a non-empty list of detected SIMs.
     *         [AppResult.Failure] with [DomainError.PermissionError.ReadPhoneStateRequired]
     *           if the READ_PHONE_STATE permission is not granted.
     *         [AppResult.Failure] with [DomainError.PaymentError.NoSim] if no SIM is present.
     */
    fun detectSims(): AppResult<List<SimInfo>, DomainError> {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE,
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            return AppResult.Failure(
                DomainError.PermissionError.ReadPhoneStateRequired(isPermanentlyDenied = false),
            )
        }

        return try {
            val subscriptionManager =
                context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                    ?: return AppResult.Failure(DomainError.PaymentError.NoSim)

            @Suppress("MissingPermission")
            val activeSubscriptions = subscriptionManager.activeSubscriptionInfoList

            if (activeSubscriptions.isNullOrEmpty()) {
                return AppResult.Failure(DomainError.PaymentError.NoSim)
            }

            val simInfoList = activeSubscriptions.map { info ->
                val operatorType = operatorResolver.resolve(info.carrierName?.toString() ?: "")
                val voiceAvailable = voiceServiceChecker.isVoiceServiceAvailable(info.subscriptionId)
                SimInfo(
                    subscriptionId = info.subscriptionId,
                    operatorName = info.carrierName?.toString() ?: "",
                    operatorType = operatorType,
                    slotIndex = info.simSlotIndex,
                    voiceServiceAvailable = voiceAvailable,
                    imsRegistered = false,
                )
            }

            AppResult.Success(simInfoList)
        } catch (e: SecurityException) {
            AppResult.Failure(
                DomainError.PermissionError.ReadPhoneStateRequired(isPermanentlyDenied = true),
            )
        } catch (e: Exception) {
            AppResult.Failure(DomainError.PaymentError.NoSim)
        }
    }
}
