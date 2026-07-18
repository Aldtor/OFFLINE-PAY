package com.offlinepay.core.connectivity

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Implementation of [ConnectivityManager.NetworkCallback] that updates the shared
 * [ConnectivityState] flow based on network events.
 *
 * State transitions per Design Section 12.4:
 * - [onAvailable] → [ConnectivityState.Online] if NET_CAPABILITY_VALIDATED, else [ConnectivityState.PartialConnectivity]
 * - [onLost] → [ConnectivityState.Offline]
 * - [onCapabilitiesChanged] → [ConnectivityState.Online] if validated, else [ConnectivityState.PartialConnectivity]
 *
 * Design reference: Section 3.8 (`:core:connectivity`), Section 12.4
 * Requirements: Req 1.6 (detect within 3s), Req 1.7 (state transitions)
 */
internal class ConnectivityCallbackImpl(
    private val stateFlow: MutableStateFlow<ConnectivityState>,
) : ConnectivityManager.NetworkCallback() {

    override fun onAvailable(network: Network) {
        // State will be updated precisely once capabilities are received
        // Pessimistically set PartialConnectivity until validated
        if (stateFlow.value == ConnectivityState.Offline || stateFlow.value == ConnectivityState.Unknown) {
            stateFlow.value = ConnectivityState.PartialConnectivity
        }
    }

    override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
        val isValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        stateFlow.value = if (isValidated) {
            ConnectivityState.Online
        } else {
            ConnectivityState.PartialConnectivity
        }
    }

    override fun onLost(network: Network) {
        stateFlow.value = ConnectivityState.Offline
    }

    override fun onUnavailable() {
        stateFlow.value = ConnectivityState.Offline
    }
}
