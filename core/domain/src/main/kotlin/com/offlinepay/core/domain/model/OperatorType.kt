package com.offlinepay.core.domain.model

import kotlinx.serialization.Serializable

/**
 * Indian mobile network operator types supported by the Offline Payment Engine.
 *
 * The [OperatorResolver] maps raw SIM operator name strings to these enum values.
 * The routing engine uses [OperatorType] to select the default offline payment method.
 *
 * Design reference: Section 3.9 (`:core:telephony`), Section 4.2 (routing algorithm)
 * Requirements: Req 3.2 (operator identification), Req 4.3 (default routing priority)
 */
@Serializable
enum class OperatorType {
    /** Airtel — default route: USSD (*99#) */
    AIRTEL,

    /** Vodafone Idea (Vi) — default route: USSD (*99#) */
    VI,

    /** BSNL — default route: USSD (*99#) */
    BSNL,

    /**
     * Reliance Jio — default route: UPI 123PAY.
     * Jio has inconsistent *99# USSD support per Req 4 design note.
     */
    JIO,

    /**
     * Any operator not matching the known list.
     * Default route: USSD first, then 123PAY fallback (Req 3.8).
     */
    OTHER,
}
