package com.offlinepay.feature.scanner.parser

import android.net.Uri
import com.offlinepay.core.domain.error.DomainError
import com.offlinepay.core.domain.model.PaymentParams
import com.offlinepay.core.domain.model.QrParseResult
import com.offlinepay.core.domain.model.QrType
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses UPI Intent URIs of the form `upi://intent://...` or `intent://...`.
 *
 * Intent-style QR codes embed payment parameters inside an Android intent URI.
 * The `pa` field is still mandatory; all others are optional.
 *
 * Priority: 20 — evaluated after BharatQR (10), before StandardUpi (30).
 *
 * Design reference: Section 5.3 (UPI URI Parser — intent variant)
 * Requirements: Req 2.12 (INTENT QR type detection)
 */
@Singleton
class UpiIntentUriParser @Inject constructor() : QrParser {

    override fun priority(): Int = 20

    override fun canParse(rawContent: String): Boolean {
        val lower = rawContent.trim().lowercase()
        return lower.startsWith("upi://intent://") || lower.startsWith("intent://")
    }

    override fun parse(rawContent: String): QrParseResult {
        val uri: Uri = try {
            Uri.parse(rawContent)
        } catch (e: Exception) {
            return QrParseResult.Failure(DomainError.QrError.MalformedUri(rawContent))
        }

        // Extract query params from the intent URI
        val pa = uri.getQueryParameter("pa")?.trim()
            ?: uri.getQueryParameter("S.pa")?.trim()
        if (pa.isNullOrEmpty()) {
            return QrParseResult.Failure(DomainError.QrError.MissingMandatoryField("pa"))
        }

        val pn = uri.getQueryParameter("pn")?.trim()?.take(50)

        // am — optional amount. Parsed with BigDecimal (never Double) and validated
        // per NPCI rules, identical to StandardUpiUriParser, so decimal intent QRs
        // are not truncated (e.g. am=0.29 must yield 29 paise, not 28) and out-of-range
        // amounts are rejected instead of flowing into a non-static payment.
        val amStr = uri.getQueryParameter("am")?.trim()
        val amountPaise: Long? = if (amStr.isNullOrEmpty()) {
            null
        } else {
            try {
                val bd = BigDecimal(amStr)
                when {
                    bd.scale() > 2 -> return QrParseResult.Failure(
                        DomainError.QrError.InvalidAmount(amStr, DomainError.AmountErrorReason.TOO_MANY_DECIMALS)
                    )
                    bd.signum() < 0 -> return QrParseResult.Failure(
                        DomainError.QrError.InvalidAmount(amStr, DomainError.AmountErrorReason.NEGATIVE)
                    )
                    bd.signum() == 0 -> return QrParseResult.Failure(
                        DomainError.QrError.InvalidAmount(amStr, DomainError.AmountErrorReason.ZERO)
                    )
                    bd > BigDecimal("100000") -> return QrParseResult.Failure(
                        DomainError.QrError.InvalidAmount(amStr, DomainError.AmountErrorReason.EXCEEDS_LIMIT)
                    )
                    else -> (bd * BigDecimal("100")).toLong()
                }
            } catch (e: NumberFormatException) {
                return QrParseResult.Failure(
                    DomainError.QrError.InvalidAmount(amStr, DomainError.AmountErrorReason.NON_NUMERIC)
                )
            }
        }
        val tr = uri.getQueryParameter("tr")?.trim()
        val tn = uri.getQueryParameter("tn")?.trim()?.take(100)
        val mc = uri.getQueryParameter("mc")?.trim()
            ?.takeIf { it.matches(Regex("""^\d{4}$""")) }
        val cu = uri.getQueryParameter("cu")?.trim()?.uppercase() ?: "INR"

        val params = PaymentParams(
            upiId = pa,
            payeeName = pn,
            amount = amountPaise,
            note = tn,
            transactionRef = tr,
            merchantCode = mc,
            qrType = QrType.INTENT,
            currency = cu,
        )
        return QrParseResult.Success(params, rawUri = rawContent)
    }
}
