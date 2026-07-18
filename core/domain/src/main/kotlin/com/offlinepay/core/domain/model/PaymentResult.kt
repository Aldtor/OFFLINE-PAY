package com.offlinepay.core.domain.model

import com.offlinepay.core.domain.error.DomainError

/**
 * The result of an offline payment attempt (USSD or 123PAY).
 *
 * This is a transient in-memory type — it flows from the payment engine to the ViewModel
 * and is immediately persisted as a [TransactionRecord] (with [TransactionStatus]).
 * It is not stored or serialised directly.
 *
 * For 123PAY, the result is always user-reported (see Req 6.5, Design Section 7.1).
 *
 * Design reference: Section 4.4 (PaymentResult sealed class)
 * Requirements: Req 5.9 (USSD success), Req 6.6–6.8 (123PAY result variants)
 */
sealed class PaymentResult {

    /**
     * Payment completed successfully.
     *
     * @param transactionId UUID of the persisted [TransactionRecord].
     * @param method The payment method used.
     * @param rawResponse Sanitised USSD response text, or null for 123PAY.
     * @param timestampMs Epoch milliseconds when the success was confirmed.
     */
    data class Success(
        val transactionId: String,
        val method: PaymentMethodType,
        val rawResponse: String? = null,
        val timestampMs: Long,
    ) : PaymentResult()

    /**
     * Payment failed with a typed domain error.
     *
     * @param transactionId UUID of the persisted [TransactionRecord].
     * @param error The domain error describing why the payment failed.
     * @param method The method that was attempted when failure occurred.
     * @param attemptedMethods All methods tried before exhausting options.
     */
    data class Failure(
        val transactionId: String,
        val error: DomainError,
        val method: PaymentMethodType,
        val attemptedMethods: List<PaymentMethodType> = emptyList(),
    ) : PaymentResult()

    /**
     * Payment is pending confirmation.
     *
     * Used for 123PAY when the user confirms success but the status is not
     * definitively verified (user-reported only).
     *
     * @param transactionId UUID of the persisted [TransactionRecord].
     * @param method The method used.
     * @param reason Why the result is pending.
     */
    data class Pending(
        val transactionId: String,
        val method: PaymentMethodType,
        val reason: PendingReason,
    ) : PaymentResult()

    /**
     * Payment result is unknown — user dismissed the 123PAY confirmation prompt.
     *
     * @param transactionId UUID of the persisted [TransactionRecord].
     * @param method The method used.
     * @param reason Optional description of why the result is unknown.
     */
    data class Unknown(
        val transactionId: String,
        val method: PaymentMethodType,
        val reason: String = "User dismissed without confirming",
    ) : PaymentResult()
}

/**
 * Reason why a [PaymentResult.Pending] status exists.
 */
enum class PendingReason {
    /** User confirmed 123PAY payment succeeded but it is unverified by the system. */
    USER_REPORTED,

    /** USSD response was ambiguous and could not be definitively parsed. */
    USSD_AMBIGUOUS,
}
