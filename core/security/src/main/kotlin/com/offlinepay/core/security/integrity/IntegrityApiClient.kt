package com.offlinepay.core.security.integrity

import android.content.Context
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Client for the Play Integrity API that fetches a fresh integrity token.
 *
 * Wraps the Play Integrity API's callback-based [StandardIntegrityManager.requestToken]
 * in a coroutine-friendly suspend function.
 *
 * Design reference: Section 8.2 (Play Integrity — Offline-Friendly Strategy)
 * Requirements: Req 9.1 (Play Integrity API integration), Req 9.2 (PASS verdict allows payments)
 */
@Singleton
class IntegrityApiClient @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Requests a fresh integrity token from the Play Integrity API.
     *
     * @param nonce A unique nonce for this request (base64-encoded, > 16 bytes, < 500 bytes).
     * @return The raw integrity token string from the API response.
     * @throws Exception if the API call fails or the device doesn't support Play Integrity.
     */
    suspend fun requestToken(nonce: String): String = suspendCancellableCoroutine { continuation ->
        val integrityManager = IntegrityManagerFactory.create(context)
        val request = IntegrityTokenRequest.builder()
            .setNonce(nonce)
            .build()

        integrityManager.requestIntegrityToken(request)
            .addOnSuccessListener { response ->
                if (continuation.isActive) {
                    continuation.resume(response.token())
                }
            }
            .addOnFailureListener { exception ->
                if (continuation.isActive) {
                    continuation.resumeWithException(exception)
                }
            }
    }
}
