package com.offlinepay.core.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.FileReader
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects the presence of Frida dynamic instrumentation framework and other
 * common runtime hooking tools (Xposed, Substrate).
 *
 * Performs three checks per Design Section 8.3:
 * 1. Inspects `/proc/self/maps` for known Frida library signatures
 * 2. Probes localhost port 27042 (Frida default) and 17777 (legacy)
 *
 * Both [checkProcMaps] and [checkFridaPorts] perform blocking I/O and are
 * dispatched to [Dispatchers.IO] via the [check] suspend function.
 *
 * Design reference: Section 8.3 (Frida / Hooking Detection)
 * Requirements: Req 9.12 (tamper detection), Req 9.14 (block on detection)
 */
@Singleton
class FridaDetector @Inject constructor() {

    /**
     * Result of a tamper/hooking detection check.
     */
    enum class TamperCheckResult {
        /** No Frida or hooking indicators found. */
        CLEAN,

        /** Frida or another instrumentation framework was detected. */
        TAMPERED,
    }

    companion object {
        /** Known library/file name fragments associated with Frida. */
        private val FRIDA_SIGNATURES = listOf(
            "frida",
            "frida-agent",
            "frida-gadget",
            "frida-server",
            "re.frida",
            "com.saurik.substrate",
            "xposed",
            "de.robv.android.xposed",
        )

        /** Localhost ports commonly used by Frida server. */
        private val FRIDA_PORTS = intArrayOf(27042, 17777)
    }

    /**
     * Runs all Frida detection checks on the IO dispatcher and returns the composite result.
     *
     * Dispatches to [Dispatchers.IO] because both [checkProcMaps] (file I/O) and
     * [checkFridaPorts] (socket connect) are blocking operations.
     *
     * @return [TamperCheckResult.TAMPERED] if any check triggers.
     *         [TamperCheckResult.CLEAN] if all checks pass.
     */
    suspend fun check(): TamperCheckResult = withContext(Dispatchers.IO) {
        when {
            checkProcMaps() -> TamperCheckResult.TAMPERED
            checkFridaPorts() -> TamperCheckResult.TAMPERED
            else -> TamperCheckResult.CLEAN
        }
    }

    /**
     * Checks `/proc/self/maps` for Frida-related memory-mapped library signatures.
     * Blocking file I/O — must be called from a background thread.
     *
     * @return true if any Frida signatures are found.
     */
    fun checkProcMaps(): Boolean {
        return try {
            BufferedReader(FileReader("/proc/self/maps")).useLines { lines ->
                lines.any { line ->
                    val lower = line.lowercase()
                    FRIDA_SIGNATURES.any { sig -> lower.contains(sig) }
                }
            }
        } catch (e: Exception) {
            // File may not be readable in some restricted environments — treat as clean
            false
        }
    }

    /**
     * Probes localhost ports commonly used by Frida server.
     * Blocking socket I/O — must be called from a background thread.
     *
     * A successful connection indicates a Frida server is likely running.
     *
     * @return true if any Frida port is open on localhost.
     */
    fun checkFridaPorts(): Boolean {
        return FRIDA_PORTS.any { port ->
            try {
                Socket("127.0.0.1", port).use { true }
            } catch (e: Exception) {
                false
            }
        }
    }
}
