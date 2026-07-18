package com.offlinepay.feature.scanner.parser

import com.offlinepay.core.domain.error.DomainError
import com.offlinepay.core.domain.model.QrParseResult
import com.offlinepay.core.domain.model.QrType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses Bharat QR codes (multi-network QR standard used by NPCI).
 *
 * If the Bharat QR embeds a `upi://` segment, this parser delegates to
 * [StandardUpiUriParser] to extract the UPI payment parameters and overrides
 * the QR type to [QrType.BHARAT]. If no UPI segment is present, it returns
 * [DomainError.QrError.UnsupportedBharatQr].
 *
 * Priority: 10 — evaluated first in the parser pipeline.
 *
 * Design reference: Section 5.3 (Parser strategies — Bharat QR)
 * Requirements: Req 2.14 (BHARAT QR type detection)
 */
@Singleton
class BharatQrParser @Inject constructor(
    private val standardUpiUriParser: StandardUpiUriParser,
) : QrParser {

    override fun priority(): Int = 10

    override fun canParse(rawContent: String): Boolean =
        rawContent.contains("BharatQR", ignoreCase = true)

    override fun parse(rawContent: String): QrParseResult {
        // Try to find an embedded upi:// segment and delegate to StandardUpiUriParser
        val upiSegmentIndex = rawContent.indexOf("upi://", ignoreCase = true)
        if (upiSegmentIndex >= 0) {
            val upiSegment = rawContent.substring(upiSegmentIndex)
            // Take only the UPI URI portion (up to any whitespace or non-URI delimiter)
            val upiUri = upiSegment.substringBefore("\n").substringBefore("\r").trim()
            if (standardUpiUriParser.canParse(upiUri)) {
                return when (val result = standardUpiUriParser.parse(upiUri)) {
                    is QrParseResult.Success -> result.copy(
                        paymentParams = result.paymentParams.copy(qrType = QrType.BHARAT),
                        rawUri = rawContent,
                    )
                    is QrParseResult.Failure -> result
                }
            }
        }

        // No usable UPI segment found — Bharat QR without UPI fields is unsupported
        return QrParseResult.Failure(
            DomainError.QrError.UnsupportedBharatQr(
                reason = "No UPI URI segment found in Bharat QR content"
            )
        )
    }
}
