package com.offlinepay.core.telephony

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.domain.model.SimInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A [BroadcastReceiver] that listens for SIM state changes
 * and triggers [SimDetector] re-detection on SIM state changes.
 *
 * Exposes updated SIM info as a [StateFlow]. Register the receiver to begin listening.
 *
 * Design reference: Section 3.9 (`:core:telephony` — SimStateBroadcastReceiver)
 * Requirements: Req 3.1 (detect SIM changes), Req 3.3 (dual-SIM support)
 */
@Singleton
class SimStateBroadcastReceiver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val simDetector: SimDetector,
) {
    private val scope = CoroutineScope(SupervisorJob())

    private val _simInfoList = MutableStateFlow<List<SimInfo>>(emptyList())

    /**
     * Current detected SIMs as a [StateFlow].
     * Updated whenever a SIM state change broadcast is received.
     */
    val simInfoList: StateFlow<List<SimInfo>> = _simInfoList.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // "android.intent.action.SIM_STATE_CHANGED" — the underlying intent action
            // for TelephonyManager.ACTION_SIM_STATE_CHANGED (deprecated constant in API 28+
            // but the intent action string remains valid)
            if (intent.action == ACTION_SIM_STATE_CHANGED) {
                scope.launch { refreshSimList() }
            }
        }
    }

    /**
     * Registers the broadcast receiver and performs an initial SIM detection.
     * Call this from the Application or an appropriate lifecycle owner.
     */
    fun register() {
        val filter = IntentFilter(ACTION_SIM_STATE_CHANGED)
        context.registerReceiver(receiver, filter)
        scope.launch { refreshSimList() }
    }

    /**
     * Unregisters the broadcast receiver.
     */
    fun unregister() {
        try {
            context.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
            // Already unregistered
        }
    }

    private fun refreshSimList() {
        val result = simDetector.detectSims()
        if (result is AppResult.Success) {
            _simInfoList.value = result.data
        } else {
            _simInfoList.value = emptyList()
        }
    }

    /**
     * Triggers an immediate re-detection without waiting for a broadcast.
     */
    fun refresh() {
        scope.launch { refreshSimList() }
    }

    companion object {
        /**
         * Intent action for SIM state changes.
         * Equivalent to the deprecated [android.telephony.TelephonyManager.ACTION_SIM_STATE_CHANGED].
         * Using the raw string to avoid the deprecation warning while maintaining compatibility.
         */
        const val ACTION_SIM_STATE_CHANGED = "android.intent.action.SIM_STATE_CHANGED"
    }
}
