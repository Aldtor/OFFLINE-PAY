package com.offlinepay.core.domain.model

import kotlinx.serialization.Serializable

/**
 * Classification of a decoded UPI QR code.
 *
 * The [QrTypeClassifier] assigns one of these types based on the fields
 * present in the decoded UPI URI.
 *
 * Design reference: Section 5.2 (QR Type Classification table)
 * Requirements: Req 2.9–2.14 (QR type detection and display)
 */
@Serializable
enum class QrType {
    /**
     * Static UPI QR — `pa` present, `am` absent.
     * Common at small merchants. Amount must be entered by the user.
     */
    STATIC,

    /**
     * Dynamic UPI QR — `pa`, `am`, and `tr` all present.
     * Merchant-generated per-transaction QR with a pre-set amount.
     */
    DYNAMIC,

    /**
     * Merchant QR — `mc` (merchant category code) field present.
     * Business UPI QR identifying the merchant category.
     */
    MERCHANT,

    /**
     * Personal QR — `pa` present, `mc` absent.
     * Individual's UPI QR with no merchant category code.
     */
    PERSONAL,

    /**
     * Intent QR — encodes a UPI deep link intent URI (`upi://intent://...`).
     */
    INTENT,

    /**
     * Bharat QR — multi-network QR standard. UPI fields extracted if present.
     */
    BHARAT,
}
