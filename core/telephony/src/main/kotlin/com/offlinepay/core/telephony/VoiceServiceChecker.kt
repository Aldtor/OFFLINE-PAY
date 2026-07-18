package com.offlinepay.core.telephony

import android.content.Context
import android.telephony.ServiceState
import android.telephony.TelephonyManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Checks whether a given SIM subscription has cellular voice service available.
 *
 * Uses [TelephonyManager.getServiceState] to query the current service state for
 * the given [subscriptionId].
 *
 * Design reference: Section 3.9 (`:core:telephony` — VoiceServiceChecker)
 * Requirements: Req 3.6 (voice service availability check), Req 3.7 (fallback if no service)
 */
@Singleton
class VoiceServiceChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Returns true if cellular voice service is currently available for [subscriptionId].
     *
     * Uses [ServiceState.getState] to determine if the SIM is registered and
     * voice service is available. Returns false on any exception (e.g., permission
     * denial, null service state).
     *
     * @param subscriptionId The Android telephony subscription ID to check.
     */
    fun isVoiceServiceAvailable(subscriptionId: Int): Boolean {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                ?: return false
            val subManager = telephonyManager.createForSubscriptionId(subscriptionId)
            val serviceState: ServiceState? = subManager.serviceState
            // STATE_IN_SERVICE (0) means the device is registered and voice is available.
            serviceState?.state == ServiceState.STATE_IN_SERVICE
        } catch (e: Exception) {
            false
        }
    }
}
