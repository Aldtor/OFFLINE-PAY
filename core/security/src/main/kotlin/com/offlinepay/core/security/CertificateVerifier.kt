package com.offlinepay.core.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verifies the app's signing certificate at runtime to detect tampered (repackaged) APKs.
 *
 * Computes the SHA-256 digest of the first signing certificate in [PackageInfo.signingInfo]
 * and compares it against the expected digest stored as a compile-time constant.
 * In release builds, this constant is obfuscated by R8.
 *
 * A mismatch indicates the APK has been repackaged (rebranded or tampered).
 *
 * Design reference: Section 8.3 (Signing Certificate Verification)
 * Requirements: Req 9.12 (certificate tampering detection), Req 9.14 (block on tamper)
 */
@Singleton
class CertificateVerifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * The result of a certificate verification check.
     */
    enum class CertCheckResult {
        /** Runtime certificate matches the expected digest. App is genuine. */
        MATCH,

        /** Runtime certificate does NOT match. App has been repackaged. */
        MISMATCH,

        /** Certificate digest could not be computed (e.g., PackageManager not available). */
        UNKNOWN,
    }

    companion object {
        /**
         * Expected SHA-256 digest of the release signing certificate in HEX format.
         *
         * **IMPORTANT**: Replace this placeholder with the actual SHA-256 fingerprint
         * of the release signing certificate before shipping to production.
         *
         * R8 will obfuscate this string constant in release builds, making extraction harder.
         *
         * To get your cert fingerprint:
         * `keytool -printcert -file your_release.jks`
         */
        private const val EXPECTED_CERT_SHA256 = "REPLACE_WITH_ACTUAL_RELEASE_CERT_SHA256_HEX"
    }

    /**
     * Verifies the app's current signing certificate against [EXPECTED_CERT_SHA256].
     *
     * @return [CertCheckResult.MATCH] if the certificate is genuine.
     *         [CertCheckResult.MISMATCH] if the certificate does not match (tampered APK).
     *         [CertCheckResult.UNKNOWN] if the check could not be performed.
     */
    fun verify(): CertCheckResult {
        return try {
            val digest = getSigningCertDigest() ?: return CertCheckResult.UNKNOWN

            // In debug builds, skip cert check to allow development
            if (isDebugBuild()) {
                return CertCheckResult.MATCH
            }

            if (digest.equals(EXPECTED_CERT_SHA256, ignoreCase = true)) {
                CertCheckResult.MATCH
            } else {
                CertCheckResult.MISMATCH
            }
        } catch (e: Exception) {
            CertCheckResult.UNKNOWN
        }
    }

    /**
     * Returns the SHA-256 hex digest of the first signing certificate, or null on failure.
     */
    fun getSigningCertDigest(): String? {
        return try {
            val packageManager = context.packageManager
            val packageName = context.packageName

            val signingInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val pkgInfo = packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES,
                )
                val signers = pkgInfo.signingInfo?.apkContentsSigners
                signers?.firstOrNull()?.toByteArray()
            } else {
                @Suppress("DEPRECATION")
                val pkgInfo = packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNATURES,
                )
                @Suppress("DEPRECATION")
                pkgInfo.signatures?.firstOrNull()?.toByteArray()
            } ?: return null

            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(signingInfo)
            hash.joinToString("") { "%02X".format(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun isDebugBuild(): Boolean {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
            (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        } catch (e: Exception) {
            false
        }
    }
}
