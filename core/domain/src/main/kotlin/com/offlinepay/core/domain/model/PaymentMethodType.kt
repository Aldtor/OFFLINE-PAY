package com.offlinepay.core.domain.model

import kotlinx.serialization.Serializable

/**
 * The offline payment method selected or attempted.
 *
 * Used in [RoutingDecision], [PaymentResult], and [TransactionRecord] to record
 * which method was used for each payment attempt.
 *
 * Design reference: Section 4.1 (PaymentMethodStrategy interface)
 * Requirements: Req 4.2 (configurable routing priority list), Req 8.2 (payment_method field)
 */
@Serializable
enum class PaymentMethodType {
    /** USSD *99# via `TelephonyManager.sendUssdRequest()` — Req 5 */
    USSD,

    /** UPI 123PAY IVR call via `ACTION_CALL` — Req 6 */
    PAY123,
}
