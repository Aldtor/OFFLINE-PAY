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
 * Parses standard UPI payment URIs of the form `upi://pay?pa=...&pn=...&am=...`.
 *
 * Handles mandatory field validation (pa, UPI ID regex), optional fields (pn, mc, am, tr, tn, cu),
 * and amount validation per NPCI limits (positive, ≤ ₹1,00,000, max 2 decimal places).
 *
 * Priority: 30 — evaluated after BharatQR (10) and UPI Intent (20).
 *
 * Design reference: Section 5.3 (UPI URI Parser)
 * Requirements: Req 2.5 (QR field extraction), Req 2.9–2.14 (QR type classification)
 */
@Singleton
class StandardUpiUriParser @Inject constructor() : QrParser {

    override fun priority(): Int = 30

    override fun canParse(rawContent: String): Boolean =
        rawContent.trim().lowercase().startsWith("upi://pay?")

    override fun parse(rawContent: String): QrParseResult {
        // 1. Parse URI
        val uri: Uri = try {
            Uri.parse(rawContent)
        } catch (e: Exception) {
            return QrParseResult.Failure(DomainError.QrError.MalformedUri(rawContent))
        }

        // 2. pa — mandatory UPI ID
        val pa = uri.getQueryParameter("pa")?.trim()
        if (pa.isNullOrEmpty()) {
            return QrParseResult.Failure(DomainError.QrError.MissingMandatoryField("pa"))
        }
        val upiIdRegex = Regex("""^[a-zA-Z0-9.\-_]{2,256}@[a-zA-Z]{2,64}$""")
        if (!upiIdRegex.matches(pa)) {
            return QrParseResult.Failure(
                DomainError.QrError.InvalidFormat(rawContent, "Invalid UPI ID: $pa")
            )
        }

        // 3. pn — optional payee name, max 50 chars
        val pn = uri.getQueryParameter("pn")?.trim()?.take(50)

        // 4. mc — optional merchant category code, must be exactly 4 digits
        val mc = uri.getQueryParameter("mc")?.trim()
            ?.takeIf { it.matches(Regex("""^\d{4}$""")) }

        // 5. am — optional amount, validated per NPCI rules
        val amStr = uri.getQueryParameter("am")?.trim()
        val amountPaise: Long? = if (amStr.isNullOrEmpty()) {
            null
        } else {
            try {
                val bd = BigDecimal(amStr)
                val scale = bd.scale()
                when {
                    scale > 2 -> return QrParseResult.Failure(
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

        // 6. tr, tn, cu — optional fields
        val tr = uri.getQueryParameter("tr")?.trim()
        val tn = uri.getQueryParameter("tn")?.trim()?.take(100)
        val cu = uri.getQueryParameter("cu")?.trim()?.uppercase() ?: "INR"

        // 7. Classify QR type
        val qrType = when {
            mc != null -> QrType.MERCHANT
            amountPaise != null && tr != null -> QrType.DYNAMIC
            else -> QrType.STATIC
        }

        val params = PaymentParams(
            upiId = pa,
            payeeName = pn,
            amount = amountPaise,
            note = tn,
            transactionRef = tr,
            merchantCode = mc,
            qrType = qrType,
            currency = cu,
        )
        return QrParseResult.Success(params, rawUri = rawContent)
    }
}
