package com.offlinepay.feature.scanner.parser

import com.offlinepay.core.domain.error.DomainError
import com.offlinepay.core.domain.model.QrParseResult
import com.offlinepay.core.domain.usecase.qr.ParseQrCodeUseCase
import javax.inject.Inject

/**
 * Concrete implementation of [ParseQrCodeUseCase] that runs the full QR parser pipeline.
 *
 * Parsers are sorted by [QrParser.priority] (ascending), and the first parser
 * whose [QrParser.canParse] returns true processes the raw QR string. If no parser
 * claims the input, [QrParseResult.Failure] with [DomainError.QrError.NonUpiContent]
 * is returned.
 *
 * Design reference: Section 5.1 (Scanner Pipeline — parser selection algorithm)
 * Requirements: Req 2.3 (UPI URI validation), Req 2.9–2.16 (QR classification)
 */
class QrParserPipeline @Inject constructor(
    private val parsers: Set<@JvmSuppressWildcards QrParser>,
) : ParseQrCodeUseCase {

    override suspend fun invoke(rawString: String): QrParseResult {
        val sortedParsers = parsers.sortedBy { it.priority() }
        for (parser in sortedParsers) {
            if (parser.canParse(rawString)) {
                return parser.parse(rawString)
            }
        }
        return QrParseResult.Failure(
            DomainError.QrError.NonUpiContent(rawString.take(100)),
        )
    }
}
