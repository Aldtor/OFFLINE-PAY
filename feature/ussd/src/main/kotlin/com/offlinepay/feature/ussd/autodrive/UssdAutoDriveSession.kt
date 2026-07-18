package com.offlinepay.feature.ussd.autodrive

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Progress of an auto-driven `*99#` session, published for the guidance UI.
 */
sealed interface UssdDriveProgress {
    /** No session armed / nothing happening. */
    data object Idle : UssdDriveProgress

    /**
     * Actively navigating a menu step.
     * @param label      Human-readable step label (e.g. "Entering amount").
     * @param stepIndex  1-based index of the current step.
     * @param totalSteps Total injectable steps in the script.
     */
    data class Navigating(val label: String, val stepIndex: Int, val totalSteps: Int) : UssdDriveProgress

    /** All menus auto-filled; the UPI PIN screen is showing — the user must type their PIN. */
    data object AwaitingPin : UssdDriveProgress

    /** Bank reported success. */
    data object Completed : UssdDriveProgress

    /** The flow failed (bank error, unmatched menu, or dropped session). */
    data class Failed(val reason: String) : UssdDriveProgress
}

/**
 * Shared, process-wide bridge between the OS-instantiated [UssdAccessibilityService]
 * and the Compose layer ([com.offlinepay.feature.ussd.UssdViewModel]).
 *
 * The controller **arms** this with the payee/amount before dialling `*99#`; the
 * accessibility service reads it to decide what to type into each USSD dialog, and
 * publishes [progress] back for the UI. Mirrors the `@Singleton` + private
 * `MutableStateFlow` + public `StateFlow` shape of
 * `com.offlinepay.core.telephony.SimStateBroadcastReceiver`.
 *
 * SECURITY: this never holds, requests, or forwards a UPI PIN. When the PIN screen is
 * reached the session transitions to [UssdDriveProgress.AwaitingPin] and stops acting.
 */
@Singleton
class UssdAutoDriveSession @Inject constructor() {

    private val _progress = MutableStateFlow<UssdDriveProgress>(UssdDriveProgress.Idle)
    val progress: StateFlow<UssdDriveProgress> = _progress.asStateFlow()

    // ── Armed payment context (guarded by `this`) ──────────────────────────────
    private var armed = false
    private var payeeUpiId: String = ""
    private var amountRupees: String = ""
    private var remark: String = ""
    private var script: UssdMenuScript = DefaultNpciScript.script

    /** Forward-only cursor into [script].steps. */
    private var cursor = 0

    /** Last dialog text we acted on, to debounce repeated content-changed events. */
    private var lastHandledText: String? = null

    /** True while a session is armed and the service should drive menus. */
    val isArmed: Boolean get() = synchronized(this) { armed }

    /**
     * Arms the session for a new payment. Call immediately before dialling `*99#`.
     *
     * @param payeeUpiId   Payee virtual payment address (the QR's `pa`).
     * @param amountRupees Amount in rupees as a plain string (e.g. "100" or "100.50").
     * @param remark       Transaction remark; blank falls back to "Payment".
     * @param script       Navigation script; defaults to the NPCI send-to-UPI-ID flow.
     */
    fun arm(
        payeeUpiId: String,
        amountRupees: String,
        remark: String,
        script: UssdMenuScript = DefaultNpciScript.script,
    ) = synchronized(this) {
        this.armed = true
        this.payeeUpiId = payeeUpiId
        this.amountRupees = amountRupees
        this.remark = remark.ifBlank { "Payment" }
        this.script = script
        this.cursor = 0
        this.lastHandledText = null
        _progress.value = UssdDriveProgress.Navigating(
            label = "Starting…",
            stepIndex = 0,
            totalSteps = injectableStepCount(script),
        )
    }

    /** Disarms and resets to [UssdDriveProgress.Idle]. */
    fun disarm() = synchronized(this) {
        armed = false
        cursor = 0
        lastHandledText = null
        _progress.value = UssdDriveProgress.Idle
    }

    /**
     * Offers a menu dialog's (already PII-sanitised for logging is NOT required here —
     * this text is used only for local keyword matching and never persisted) raw text to
     * the session and returns the exact string to type, or `null` if the service should
     * not act (no matching step, or end-of-script).
     *
     * Forward-only: consumes the first matching step at/after the cursor.
     */
    fun onMenu(rawText: String): String? = synchronized(this) {
        if (!armed) return null

        val lower = rawText.lowercase()
        // Debounce duplicate content-changed events for the same screen.
        if (lower == lastHandledText) return null

        val steps = script.steps
        var i = cursor
        while (i < steps.size) {
            val step = steps[i]
            if (step.matchers.any { lower.contains(it) }) {
                cursor = i + 1
                lastHandledText = lower
                return when (val r = step.response) {
                    is StepResponse.Digit -> {
                        publishNavigating(step.label, i)
                        r.value
                    }
                    StepResponse.PayeeUpiId -> {
                        publishNavigating(step.label, i)
                        payeeUpiId
                    }
                    StepResponse.AmountRupees -> {
                        publishNavigating(step.label, i)
                        amountRupees
                    }
                    StepResponse.Remark -> {
                        publishNavigating(step.label, i)
                        remark
                    }
                    StepResponse.StopForPin -> {
                        _progress.value = UssdDriveProgress.AwaitingPin
                        null
                    }
                }
            }
            i++
        }
        // No step matched — leave the dialog for the user.
        null
    }

    /** Called by the service when the parser classifies a PIN prompt. */
    fun markAwaitingPin() = synchronized(this) {
        if (armed) _progress.value = UssdDriveProgress.AwaitingPin
    }

    /** Called by the service when the parser classifies bank success. */
    fun markCompleted() = synchronized(this) {
        if (armed) {
            _progress.value = UssdDriveProgress.Completed
            armed = false
        }
    }

    /** Called by the service when the parser classifies a bank error / the flow fails. */
    fun markFailed(reason: String) = synchronized(this) {
        if (armed) {
            _progress.value = UssdDriveProgress.Failed(reason)
            armed = false
        }
    }

    private fun publishNavigating(label: String, stepIdx: Int) {
        _progress.value = UssdDriveProgress.Navigating(
            label = label,
            stepIndex = stepIdx + 1,
            totalSteps = injectableStepCount(script),
        )
    }

    private fun injectableStepCount(script: UssdMenuScript): Int =
        script.steps.count { it.response !is StepResponse.StopForPin }
}
