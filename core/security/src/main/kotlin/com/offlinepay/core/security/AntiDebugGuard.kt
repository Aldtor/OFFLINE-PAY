package com.offlinepay.core.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Debug
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects debugger attachment and debug build variants in release builds.
 *
 * Checks three indicators per Design Section 8.3:
 * 1. [Debug.isDebuggerConnected()] — active debugger via JDWP
 * 2. [ApplicationInfo.FLAG_DEBUGGABLE] — APK compiled with debuggable=true
 * 3. Presence of a running debug connection (waitingForDebugger)
 *
 * In a release build, any of these being true is a security signal.
 * Callers should check the result and enforce appropriate restrictions.
 *
 * Design reference: Section 8.3 (Anti-Debug Detection)
 * Requirements: Req 9.13 (debugger detection), Req 9.14 (block on detection)
 */
@Singleton
class AntiDebugGuard @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * The result of the anti-debug check.
     */
    enum class DebugStatus {
        /** No debugger detected. App is running normally. */
        CLEAN,

        /** Debugger is attached or the APK is a debuggable build. */
        DEBUGGER_ATTACHED,
    }

    /**
     * Performs all anti-debug checks and returns the composite status.
     *
     * @return [DebugStatus.CLEAN] if no debug indicators are found.
     *         [DebugStatus.DEBUGGER_ATTACHED] if any check triggers.
     */
    fun check(): DebugStatus {
        return when {
            isDebuggerConnected() -> DebugStatus.DEBUGGER_ATTACHED
            isDebuggableApk() -> DebugStatus.DEBUGGER_ATTACHED
            else -> DebugStatus.CLEAN
        }
    }

    /**
     * Returns true if a Java debugger (JDWP) is currently connected.
     */
    fun isDebuggerConnected(): Boolean =
        Debug.isDebuggerConnected() || Debug.waitingForDebugger()

    /**
     * Returns true if the APK was compiled with the `debuggable` flag.
     * Release APKs signed for Play Store should never have this flag set.
     */
    fun isDebuggableApk(): Boolean {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
            (appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        } catch (e: Exception) {
            false
        }
    }
}
