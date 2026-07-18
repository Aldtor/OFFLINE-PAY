package com.offlinepay.core.analytics

import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.perf.metrics.Trace
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wrapper around [FirebasePerformance] for custom performance traces.
 *
 * Provides named traces for critical user flows:
 * - `qr_decode` — QR code scanning to parsed result
 * - `payment_flow` — Confirm payment to payment result screen
 *
 * Design reference: Section 13.4
 * Requirements: Req 13.2 (performance monitoring), Req 13.4 (custom traces)
 */
@Singleton
class PerformanceTracer @Inject constructor() {

    private val firebasePerformance: FirebasePerformance by lazy {
        FirebasePerformance.getInstance()
    }

    private val activeTraces = mutableMapOf<String, Trace>()

    /**
     * Starts a named performance trace.
     *
     * @param traceName The name of the trace (e.g., "qr_decode", "payment_flow").
     */
    fun startTrace(traceName: String) {
        val trace = firebasePerformance.newTrace(traceName)
        trace.start()
        activeTraces[traceName] = trace
    }

    /**
     * Stops a previously started named trace.
     *
     * @param traceName The name of the trace to stop.
     */
    fun stopTrace(traceName: String) {
        activeTraces.remove(traceName)?.stop()
    }

    /**
     * Adds a metric to an active trace.
     */
    fun putMetric(traceName: String, metricName: String, value: Long) {
        activeTraces[traceName]?.putMetric(metricName, value)
    }

    companion object {
        const val TRACE_QR_DECODE = "qr_decode"
        const val TRACE_PAYMENT_FLOW = "payment_flow"
    }
}
