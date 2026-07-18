package com.offlinepay.core.domain.model

import com.offlinepay.core.domain.error.DomainError

/**
 * The result of parsing a decoded QR code string through the QR parser pipeline.
 *
 * This is a transient in-memory type — it flows from the scanner pipeline to the
 * payment confirmation ViewModel and is never persisted or serialised.
 *
 * The sealed class mirrors the `AppResult` pattern used elsewhere in the domain layer:
 * [Success] carries the valid [PaymentParams], [Failure] carries the parse error.
 *
 * Design reference: Section 5.1 (Scanner Pipeline), Section 5.3 (UPI URI Parser)
 * Requirements: Req 2.5 (field extraction), Req 2.9–2.14 (QR type display)
 */
sealed class QrParseResult {

    /**
     * QR code parsed successfully.
     *
     * @param paymentParams The extracted and validated payment parameters, ready for
     *   use by the [com.offlinepay.core.domain.payment.RoutingEngine].
     * @param rawUri The original decoded QR string (for debugging only — not stored).
     */
    data class Success(
        val paymentParams: PaymentParams,
        val rawUri: String = "",
    ) : QrParseResult()

    /**
     * QR code parsing failed.
     *
     * @param error The specific [DomainError.QrError] variant describing the failure.
     *   Maps to the [QrError] taxonomy in Design Section 5.3 and the UI error table
     *   in Design Section 13.2.
     * @param detectedContentType For non-UPI QR codes — the detected content type
     *   (e.g. `"URL"`, `"Contact"`, `"Wi-Fi"`) shown in the error state (Req 2.15).
     */
    data class Failure(
        val error: DomainError.QrError,
        val detectedContentType: String? = null,
    ) : QrParseResult()
}
