package com.offlinepay.feature.scanner.parser

import com.offlinepay.core.domain.error.DomainError
import com.offlinepay.core.domain.model.QrParseResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Catch-all classifier for QR codes that are not UPI payment URIs.
 *
 * Always returns [QrParseResult.Failure] with [DomainError.QrError.NonUpiContent],
 * identifying the content type so the UI can show a meaningful error (e.g.
 * "This is a URL QR code, not a UPI payment QR").
 *
 * Priority: 99 — evaluated last, after all other parsers have declined.
 *
 * Design reference: Section 5.3 (Parser strategies — non-UPI classifier)
 * Requirements: Req 2.15 (non-UPI content error with detected type)
 */
@Singleton
class NonUpiContentClassifier @Inject constructor() : QrParser {

    override fun priority(): Int = 99

    override fun canParse(rawContent: String): Boolean = true

    override fun parse(rawContent: String): QrParseResult {
        val contentType = detectContentType(rawContent)
        return QrParseResult.Failure(
            error = DomainError.QrError.NonUpiContent(contentType),
            detectedContentType = contentType,
        )
    }

    private fun detectContentType(rawContent: String): String = when {
        rawContent.startsWith("http://", ignoreCase = true) ||
            rawContent.startsWith("https://", ignoreCase = true) -> "URL"
        rawContent.startsWith("tel:", ignoreCase = true) -> "Phone"
        rawContent.startsWith("WIFI:", ignoreCase = true) -> "Wi-Fi"
        rawContent.startsWith("BEGIN:VCARD", ignoreCase = true) -> "Contact"
        rawContent.startsWith("smsto:", ignoreCase = true) ||
            rawContent.startsWith("sms:", ignoreCase = true) -> "SMS"
        rawContent.startsWith("mailto:", ignoreCase = true) -> "Email"
        else -> "Unknown"
    }
}
