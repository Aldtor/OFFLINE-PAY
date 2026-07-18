package com.offlinepay.feature.ussd.autodrive

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.offlinepay.feature.ussd.UssdResponseParser
import com.offlinepay.feature.ussd.UssdResponseType
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Auto-drives the interactive `*99#` USSD dialog chain so the user only has to enter
 * their UPI PIN.
 *
 * While a session is armed on [UssdAutoDriveSession] (by `UssdController` right before it
 * dials `*99#`), this service watches the system USSD dialogs, reads each menu, and:
 *  - types the scripted menu selection / payee UPI ID / amount and presses **Send**, OR
 *  - stops and hands control to the user when the **UPI PIN** prompt appears, OR
 *  - reports terminal success / bank error back to the session.
 *
 * SECURITY / PRIVACY:
 *  - It NEVER reads, types, stores, or logs a UPI PIN. On a [UssdResponseType.PIN_PROMPT]
 *    it does nothing to the dialog and marks the session [UssdDriveProgress.AwaitingPin].
 *  - Menu text is used only for local keyword matching; any debug log is PII-sanitised via
 *    [UssdResponseParser.sanitise].
 *  - It only acts while a session is armed, so it is dormant during normal phone use.
 *
 * Requirements: Req 5.1 (USSD initiation), Req 5.5/5.6/5.8 (parse / sanitise / PIN gate).
 */
@AndroidEntryPoint
class UssdAccessibilityService : AccessibilityService() {

    @Inject lateinit var session: UssdAutoDriveSession
    @Inject lateinit var parser: UssdResponseParser

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !session.isArmed) return

        // Ignore events from our own app (the guidance screen), only drive system dialogs.
        val pkg = event.packageName?.toString()
        if (pkg != null && pkg.startsWith(APP_PACKAGE_PREFIX)) return

        val root = rootInActiveWindow ?: return
        try {
            handleWindow(root)
        } catch (e: Exception) {
            Log.w(TAG, "USSD auto-drive error: ${e.message}")
        } finally {
            root.recycle()
        }
    }

    override fun onInterrupt() { /* no-op */ }

    // ── Core handling ──────────────────────────────────────────────────────────

    private fun handleWindow(root: AccessibilityNodeInfo) {
        val editText = findEditText(root)
        val message = extractMessage(root, editText)
        if (message.isBlank()) return

        val type = parser.parseUssdResponse(message).type
        Log.d(TAG, "USSD screen [$type]: ${parser.sanitise(message).take(160)}")

        when (type) {
            UssdResponseType.PIN_PROMPT -> {
                // Never touch the PIN field — hand back to the user.
                session.markAwaitingPin()
            }
            UssdResponseType.SUCCESS -> session.markCompleted()
            UssdResponseType.BANK_ERROR -> session.markFailed("Bank reported an error")
            UssdResponseType.BANK_MENU,
            UssdResponseType.UNKNOWN -> {
                val reply = session.onMenu(message) ?: return
                if (editText != null) {
                    typeAndSend(editText, reply, root)
                }
            }
        }
    }

    /** Sets [reply] into [editText] and clicks the positive/Send button. */
    private fun typeAndSend(editText: AccessibilityNodeInfo, reply: String, root: AccessibilityNodeInfo) {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, reply)
        }
        val set = editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (!set) {
            Log.w(TAG, "ACTION_SET_TEXT failed — device may block USSD-dialog injection")
            return
        }
        val button = findPositiveButton(root)
        button?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    // ── Node-tree helpers ──────────────────────────────────────────────────────

    /** Depth-first search for the first editable ([android.widget.EditText]) node. */
    private fun findEditText(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable || node.className?.contains("EditText") == true) return node
        for (i in 0 until node.childCount) {
            findEditText(node.getChild(i))?.let { return it }
        }
        return null
    }

    /**
     * Concatenates visible non-editable, non-button text (the USSD menu message).
     * Excludes [editText] so the value we just typed does not re-trigger handling.
     */
    private fun extractMessage(root: AccessibilityNodeInfo, editText: AccessibilityNodeInfo?): String {
        val sb = StringBuilder()
        fun walk(node: AccessibilityNodeInfo?) {
            if (node == null) return
            val isButton = node.className?.contains("Button") == true
            val isEdit = node == editText || node.isEditable
            if (!isButton && !isEdit) {
                node.text?.let { if (it.isNotBlank()) sb.append(it).append('\n') }
            }
            for (i in 0 until node.childCount) walk(node.getChild(i))
        }
        walk(root)
        return sb.toString().trim()
    }

    /**
     * Finds the affirmative dialog button (Send / OK / Submit), falling back to the first
     * clickable Button that is not a Cancel/Dismiss.
     */
    private fun findPositiveButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val buttons = mutableListOf<AccessibilityNodeInfo>()
        fun walk(node: AccessibilityNodeInfo?) {
            if (node == null) return
            if (node.className?.contains("Button") == true && node.isClickable) buttons.add(node)
            for (i in 0 until node.childCount) walk(node.getChild(i))
        }
        walk(root)
        val positive = buttons.firstOrNull { b ->
            val t = b.text?.toString()?.lowercase().orEmpty()
            POSITIVE_LABELS.any { t.contains(it) }
        }
        if (positive != null) return positive
        // Fallback: first button that is clearly not a cancel/dismiss.
        return buttons.firstOrNull { b ->
            val t = b.text?.toString()?.lowercase().orEmpty()
            NEGATIVE_LABELS.none { t.contains(it) }
        } ?: buttons.firstOrNull()
    }

    companion object {
        private const val TAG = "UssdAutoDrive"
        private const val APP_PACKAGE_PREFIX = "com.offlinepay"
        private val POSITIVE_LABELS = listOf("send", "ok", "submit", "proceed", "yes")
        private val NEGATIVE_LABELS = listOf("cancel", "dismiss", "no", "back")
    }
}
