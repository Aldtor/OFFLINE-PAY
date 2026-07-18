package com.offlinepay.core.domain.usecase.qr

import com.offlinepay.core.domain.model.QrParseResult

/**
 * Use case interface for parsing a raw decoded QR string into a [QrParseResult].
 *
 * The actual parsing logic lives in `:feature:scanner` (QR parser pipeline).
 * This interface allows use cases and ViewModels to depend on an abstraction
 * rather than the concrete ML Kit / ZXing implementation.
 *
 * Returns [QrParseResult.Success] on a valid UPI QR, or [QrParseResult.Failure]
 * with a [com.offlinepay.core.domain.error.DomainError.QrError] on any parse failure.
 *
 * Design reference: Section 5.1 (Scanner Pipeline), Section 5.4 (QrParser interface),
 *   Section 3.3 (Use Cases)
 * Requirements: Req 2.3–2.16 (QR validation, classification, and parsing)
 */
fun interface ParseQrCodeUseCase {

    /**
     * Parses [rawString] (the decoded QR content) into a [QrParseResult].
     *
     * @param rawString The raw string decoded by ML Kit or ZXing.
     * @return [QrParseResult.Success] with the extracted [com.offlinepay.core.domain.model.PaymentParams].
     *         [QrParseResult.Failure] with a [com.offlinepay.core.domain.error.DomainError.QrError]
     *         describing why parsing failed.
     */
    suspend operator fun invoke(rawString: String): QrParseResult
}
