package com.offlinepay.core.connectivity

/**
 * Sealed class representing the current network connectivity state.
 *
 * Updated by [ConnectivityCallbackImpl] in response to [ConnectivityManager.NetworkCallback]
 * events. Exposed as a [kotlinx.coroutines.flow.StateFlow] by [ConnectivityMonitor].
 *
 * Design reference: Section 3.8 (`:core:connectivity`), Section 12.4 (state machine)
 * Requirements: Req 1.2 (offline-first), Req 1.6 (detect within 3s), Req 1.7 (state transitions)
 */
sealed class ConnectivityState {

    /**
     * Device has a validated internet connection.
     * Corresponds to [android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED].
     */
    data object Online : ConnectivityState()

    /**
     * Device has no active network.
     * Triggered by [ConnectivityManager.NetworkCallback.onLost].
     */
    data object Offline : ConnectivityState()

    /**
     * Device has a network but it is not validated (captive portal, metered, etc.).
     * Triggered by [ConnectivityManager.NetworkCallback.onCapabilitiesChanged]
     * when NET_CAPABILITY_VALIDATED is absent.
     */
    data object PartialConnectivity : ConnectivityState()

    /**
     * Connectivity state has not yet been determined.
     * Initial state before the first callback is received.
     */
    data object Unknown : ConnectivityState()
}
