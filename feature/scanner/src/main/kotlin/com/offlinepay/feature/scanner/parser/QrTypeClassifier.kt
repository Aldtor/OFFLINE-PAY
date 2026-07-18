package com.offlinepay.feature.scanner.parser

import com.offlinepay.core.domain.model.PaymentParams
import com.offlinepay.core.domain.model.QrType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Classifies the [QrType] of a decoded QR code based on the raw content string
 * and the already-extracted [PaymentParams].
 *
 * This classifier is used as a post-parse step to override or confirm the QR type
 * when context from the raw content is needed (e.g. detecting Bharat QR or Intent URI
 * wrappers that the individual parsers may not fully handle).
 *
 * Classification precedence (highest to lowest):
 * 1. [QrType.BHARAT]   — raw content contains "BharatQR" (case-insensitive)
 * 2. [QrType.INTENT]   — raw content starts with `upi://intent://` or `intent://`
 * 3. [QrType.DYNAMIC]  — `amount` and `transactionRef` are both present
 * 4. [QrType.MERCHANT] — `merchantCode` is present
 * 5. [QrType.PERSONAL] — `pa` present, no merchant code (default static/personal)
 *
 * Design reference: Section 5.2 (QR Type Classification table)
 * Requirements: Req 2.9–2.14 (QR type detection and display)
 */
@Singleton
class QrTypeClassifier @Inject constructor() {

    /**
     * Classifies the QR type from [rawContent] and [params].
     *
     * @param rawContent The original decoded QR string.
     * @param params The [PaymentParams] extracted by the parser pipeline.
     * @return The most specific [QrType] that matches the content.
     */
    fun classifyQrType(rawContent: String, params: PaymentParams): QrType = when {
        rawContent.contains("BharatQR", ignoreCase = true) -> QrType.BHARAT
        rawContent.startsWith("upi://intent://", ignoreCase = true) ||
            rawContent.startsWith("intent://", ignoreCase = true) -> QrType.INTENT
        params.amount != null && params.transactionRef != null -> QrType.DYNAMIC
        params.merchantCode != null -> QrType.MERCHANT
        else -> QrType.PERSONAL // pa present, no mc = personal/static
    }
}
