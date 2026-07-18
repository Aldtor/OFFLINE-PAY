package com.offlinepay.core.domain.model

import kotlinx.serialization.Serializable
import java.io.Serializable as JavaSerializable

/**
 * Parameters extracted from a scanned UPI QR code and used to initiate a payment.
 *
 * All amounts are stored as **paise** (Long) to avoid floating-point rounding errors.
 * A null [amount] indicates a static QR — the user must enter the amount manually.
 *
 * Design reference: Section 4.4 (PaymentParams data model)
 * Requirements: Req 2.5 (QR field extraction), Req 7.1 (payment confirmation screen fields)
 *
 * @param upiId Mandatory payee UPI ID (`pa` field). Must pass [String.isValidUpiId].
 * @param payeeName Optional display name of the payee (`pn` field).
 * @param amount Optional amount in paise (`am` field × 100). Null for static QRs.
 * @param note Optional transaction note (`tn` field).
 * @param transactionRef Optional QR transaction reference (`tr` field).
 * @param merchantCode Optional ISO 18245 merchant category code (`mc` field).
 * @param qrType The classified type of the QR code.
 * @param currency Currency code — always `"INR"` for NPCI UPI.
 */
@Serializable
data class PaymentParams(
    val upiId: String,
    val payeeName: String? = null,
    val amount: Long? = null,
    val note: String? = null,
    val transactionRef: String? = null,
    val merchantCode: String? = null,
    val qrType: QrType = QrType.STATIC,
    val currency: String = "INR",
) : JavaSerializable
