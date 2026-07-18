package com.offlinepay.core.domain.usecase.qr

import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.domain.error.DomainError
import com.offlinepay.core.domain.model.QrParseResult

/**
 * Contract for a single QR parser in the parser pipeline.
 *
 * Concrete implementations live in `:feature:scanner` (ML Kit, ZXing, UPI URI parsers)
 * and are registered via Hilt `Set<QrParser>` multibinding. The domain layer owns this
 * interface as the inversion point, keeping `:core:domain` free of Android dependencies.
 *
 * Parsers are ordered by [priority] — lower value = higher priority. The pipeline
 * iterates parsers in ascending priority order, returning the first successful parse.
 *
 * Registered parsers (priority order — Design Section 5.4):
 * 1. `BharatQrParser` (priority 10)
 * 2. `UpiIntentUriParser` (priority 20)
 * 3. `StandardUpiUriParser` (priority 30)
 * 4. `NonUpiContentClassifier` (priority 99) — fallback classifier
 *
 * Design reference: Section 5.4 (Extensibility — QrParser interface)
 * Requirements: Req 2.3–2.16 (QR validation, classification, parsing)
 */
interface QrParser {

    /**
     * Returns true if this parser can attempt to handle [rawString].
     *
     * This is a lightweight pre-check; it MUST NOT perform full parsing.
     * The pipeline calls [parse] only when [canParse] returns true.
     */
    fun canParse(rawString: String): Boolean

    /**
     * Parses [rawString] into a [QrParseResult].
     *
     * MUST only be called when [canParse] returns true.
     *
     * @return [AppResult.Success] wrapping [QrParseResult.Success] on valid UPI QR.
     *         [AppResult.Failure] wrapping [DomainError.QrError] on any parse failure.
     */
    fun parse(rawString: String): AppResult<QrParseResult, DomainError.QrError>

    /**
     * Priority of this parser. Lower value = higher priority.
     * Used by the pipeline to sort parsers before iterating.
     */
    fun priority(): Int
}
