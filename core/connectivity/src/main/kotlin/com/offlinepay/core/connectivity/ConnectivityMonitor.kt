package com.offlinepay.core.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkRequest
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors network connectivity state using [ConnectivityManager] callbacks.
 * Exposes connectivity state as a [StateFlow] — no polling is used.
 *
 * Registered with [ConnectivityManager.registerDefaultNetworkCallback] in init block.
 * Unregisters the callback when the [CoroutineScope] is cancelled.
 *
 * Per Req 1.6: state transitions are detected within 3 seconds of change.
 *
 * Design reference: Section 3.8 (`:core:connectivity`), Section 12.4 (state machine)
 * Requirements: Req 1.2 (offline-first), Req 1.6, Req 1.7
 */
@Singleton
class ConnectivityMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _connectivityState = MutableStateFlow<ConnectivityState>(ConnectivityState.Unknown)

    /**
     * Current connectivity state as a [StateFlow].
     * Replays the most recent state immediately to new collectors.
     */
    val connectivityState: StateFlow<ConnectivityState> = _connectivityState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob())
    private val callback = ConnectivityCallbackImpl(_connectivityState)
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    init {
        // Determine initial state from active network
        val activeNetwork = connectivityManager.activeNetwork
        val activeCapabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        _connectivityState.value = when {
            activeCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true ->
                ConnectivityState.Online
            activeCapabilities != null -> ConnectivityState.PartialConnectivity
            else -> ConnectivityState.Offline
        }

        // Register for future network changes
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)
    }

    /**
     * Releases the network callback. Call when the monitor is no longer needed.
     * In practice, since this is a @Singleton, it lives for the app lifetime.
     */
    fun release() {
        try {
            connectivityManager.unregisterNetworkCallback(callback)
        } catch (_: IllegalArgumentException) {
            // Already unregistered
        }
        scope.cancel()
    }
}
