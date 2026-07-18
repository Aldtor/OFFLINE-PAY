package com.offlinepay.feature.scanner.parser

import com.offlinepay.core.domain.model.QrParseResult

/**
 * Contract for all QR code parser strategies in the scanner pipeline.
 *
 * Parsers are discovered via Hilt's `@IntoSet` multibinding and sorted by
 * [priority] (lower = higher priority). The pipeline calls [canParse] on each
 * parser in order and delegates to the first one that matches.
 *
 * Design reference: Section 5.1 (Scanner Pipeline), Section 5.3 (Parser strategies)
 */
interface QrParser {
    /**
     * Returns true if this parser recognises the format of [rawContent].
     * Should be a fast, non-throwing check (no network or heavy I/O).
     */
    fun canParse(rawContent: String): Boolean

    /**
     * Parses [rawContent] and returns a [QrParseResult].
     * Only called after [canParse] returned true.
     */
    fun parse(rawContent: String): QrParseResult

    /**
     * Lower value = higher priority.
     * BharatQR=10, UpiIntent=20, StandardUpi=30, NonUpiClassifier=99
     */
    fun priority(): Int
}
