package com.offlinepay.core.security.integrity

import com.offlinepay.core.domain.model.IntegrityVerdict
import com.offlinepay.core.domain.model.VerdictType
import org.json.JSONObject
import android.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses a raw Play Integrity API token into an [IntegrityVerdict] domain model.
 *
 * The token is a JWT (JSON Web Token). The payload (second segment, base64url-encoded)
 * contains the verdict fields. This parser decodes the payload and extracts the
 * `deviceIntegrity.deviceRecognitionVerdict` array to determine the pass/fail status.
 *
 * Per Design Section 8.2:
 * - MEETS_BASIC_INTEGRITY or better → [VerdictType.PASS]
 * - NO_INTEGRITY or any failure → [VerdictType.FAIL]
 *
 * Design reference: Section 8.2 (Play Integrity verdict parsing)
 * Requirements: Req 9.1, Req 9.2, Req 9.5
 */
@Singleton
class VerdictParser @Inject constructor() {

    companion object {
        private const val VERDICT_MEETS_BASIC = "MEETS_BASIC_INTEGRITY"
        private const val VERDICT_MEETS_DEVICE = "MEETS_DEVICE_INTEGRITY"
        private const val VERDICT_MEETS_STRONG = "MEETS_STRONG_INTEGRITY"
        private const val VERDICT_NO_INTEGRITY = "NO_INTEGRITY"
    }

    /**
     * Parses the raw JWT token from the Play Integrity API into an [IntegrityVerdict].
     *
     * @param token The raw integrity token (JWT string) from [IntegrityApiClient.requestToken].
     * @param requestTimeMs Epoch milliseconds when the token was requested (cached timestamp).
     * @return An [IntegrityVerdict] with the parsed verdict and timestamp.
     */
    fun parse(token: String, requestTimeMs: Long): IntegrityVerdict {
        return try {
            val payload = decodeJwtPayload(token)
            val verdictType = extractVerdictType(payload)
            val isGenuine = verdictType == VerdictType.PASS
            IntegrityVerdict(
                isGenuineDevice = isGenuine,
                isAppValid = isGenuine,
                isEnvironmentSafe = isGenuine,
                verdict = verdictType,
                cachedAtMs = requestTimeMs,
                rawVerdict = null, // Raw verdict not stored per Section 8.5 data classification
            )
        } catch (e: Exception) {
            // On parse failure, treat as FAIL (conservative security stance)
            IntegrityVerdict.fail(
                isGenuineDevice = false,
                isAppValid = false,
                isEnvironmentSafe = false,
                cachedAtMs = requestTimeMs,
            )
        }
    }

    /**
     * Decodes the JWT payload segment and parses it as JSON.
     *
     * JWT format: header.payload.signature (dot-separated, base64url-encoded)
     */
    private fun decodeJwtPayload(token: String): JSONObject {
        val parts = token.split(".")
        require(parts.size >= 2) { "Invalid JWT: expected at least 2 segments" }
        val payloadBase64 = parts[1]
        // base64url → standard base64 (replace - with + and _ with /)
        val standardBase64 = payloadBase64
            .replace('-', '+')
            .replace('_', '/')
        // Add padding if necessary
        val padded = when (standardBase64.length % 4) {
            2 -> "$standardBase64=="
            3 -> "$standardBase64="
            else -> standardBase64
        }
        val decodedBytes = Base64.decode(padded, Base64.DEFAULT)
        return JSONObject(String(decodedBytes, Charsets.UTF_8))
    }

    /**
     * Extracts the verdict type from the decoded payload JSON.
     *
     * The `deviceIntegrity.deviceRecognitionVerdict` array is the authoritative source.
     * If it contains any of the MEETS_* values, the verdict is [VerdictType.PASS].
     */
    private fun extractVerdictType(payload: JSONObject): VerdictType {
        val deviceIntegrity = payload.optJSONObject("deviceIntegrity") ?: return VerdictType.FAIL
        val verdictArray = deviceIntegrity.optJSONArray("deviceRecognitionVerdict") ?: return VerdictType.FAIL

        val verdictList = mutableListOf<String>()
        for (i in 0 until verdictArray.length()) {
            verdictList.add(verdictArray.getString(i))
        }

        return when {
            verdictList.any { it == VERDICT_MEETS_STRONG || it == VERDICT_MEETS_DEVICE || it == VERDICT_MEETS_BASIC } ->
                VerdictType.PASS
            else -> VerdictType.FAIL
        }
    }
}
