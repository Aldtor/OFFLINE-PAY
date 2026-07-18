package com.offlinepay.core.domain.model

import kotlinx.serialization.Serializable

/**
 * An immutable record of a single offline payment attempt stored in the local database.
 *
 * All monetary amounts use paise (Long) to avoid floating-point rounding errors.
 * This is the domain model — the corresponding Room entity is `TransactionEntity`
 * in `:core:data` and must never be exposed outside the data layer.
 *
 * Design reference: Section 9.1 (transactions table schema)
 * Requirements: Req 8.1 (persist every payment attempt), Req 8.2 (all required fields)
 *
 * @param id UUID v4 string — primary key.
 * @param timestampMs Epoch milliseconds when the payment was initiated.
 * @param payeeUpiId The payee's UPI ID (`pa` field from QR).
 * @param payeeName Optional display name of the payee.
 * @param merchantName Optional merchant name (may differ from payee name).
 * @param merchantCategoryCode Optional ISO 18245 merchant category code.
 * @param amountPaise Payment amount in paise.
 * @param paymentMethod Which offline method was used.
 * @param status The final status of this payment attempt.
 * @param operatorUsed The operator type at time of transaction.
 * @param simSlotUsed SIM slot index (0 or 1).
 * @param ussdResponse Sanitised USSD response text — UPI PINs and PII stripped.
 * @param transactionReference QR-provided `tr` field or USSD-extracted reference.
 * @param createdAt Epoch milliseconds when this record was created.
 * @param updatedAt Epoch milliseconds when this record was last updated.
 */
@Serializable
data class TransactionRecord(
    val id: String,
    val timestampMs: Long,
    val payeeUpiId: String,
    val payeeName: String? = null,
    val merchantName: String? = null,
    val merchantCategoryCode: String? = null,
    val amountPaise: Long,
    val paymentMethod: PaymentMethodType,
    val status: TransactionStatus,
    val operatorUsed: OperatorType? = null,
    val simSlotUsed: Int = 0,
    val ussdResponse: String? = null,
    val transactionReference: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * The status of a [TransactionRecord].
 *
 * Design reference: Section 9.1 (status ENUM)
 * Requirements: Req 8.2 (status field values)
 */
@Serializable
enum class TransactionStatus {
    /** Payment confirmed successful (USSD success response or user-confirmed 123PAY). */
    SUCCESS,

    /** Payment failed (USSD failure, timeout, or user-confirmed 123PAY failure). */
    FAILURE,

    /**
     * Payment initiated but result not yet confirmed.
     * Used immediately after initiating to survive process death.
     */
    PENDING,

    /**
     * User dismissed the 123PAY result confirmation without answering.
     * Status is indeterminate — user may update later.
     */
    UNKNOWN,
}
