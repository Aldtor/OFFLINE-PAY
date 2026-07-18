package com.offlinepay.core.common.extensions

import com.offlinepay.core.common.format.UpiAmountFormatter

// ── UPI ID validation ──────────────────────────────────────────────────────────

/**
 * NPCI UPI ID validation regex.
 *
 * UPI ID format: `localpart@handle`
 * - localpart: alphanumeric, dots, hyphens, underscores (1–256 chars total)
 * - @ separator: exactly one
 * - handle: only letters (3+ chars) — the VPA handle (e.g., upi, paytm, okaxis)
 *
 * Examples of valid UPI IDs:
 * - `merchant@upi`
 * - `john.doe@okaxis`
 * - `9876543210@jio`
 * - `shop-name_123@icici`
 *
 * Reference: NPCI UPI Procedural Guidelines, Section 4.2 (VPA format)
 */
private val UPI_ID_REGEX = Regex(
    pattern = "^[a-zA-Z0-9._\\-]{1,}@[a-zA-Z]{3,}\$",
)

/** Maximum allowed UPI ID length per design Section 5.3 */
private const val MAX_UPI_ID_LENGTH = 256

/**
 * Returns `true` if this [String] is a valid NPCI UPI ID.
 *
 * Validates against the NPCI VPA format: `localpart@handle`.
 * Case-insensitive, no whitespace allowed.
 *
 * Design reference: Section 5.3 (UPI URI Parser — `pa` field validation)
 * Requirements: Req 2.5 (QR field extraction), Req 7.3 (payment validation)
 *
 * @return `true` if this string matches the UPI ID format and is within length bounds.
 */
fun String.isValidUpiId(): Boolean {
    if (isBlank() || length > MAX_UPI_ID_LENGTH) return false
    return UPI_ID_REGEX.matches(this.trim())
}

// ── Paise / Rupee conversions ─────────────────────────────────────────────────

/**
 * Converts this Long value (representing paise) to a formatted rupee display string.
 *
 * Delegates to [UpiAmountFormatter.formatPaiseToRupees].
 *
 * Example: `10050L.toRupeeString()` → `"₹100.50"`
 *
 * @return Formatted rupee string with the ₹ symbol.
 * @throws IllegalArgumentException if this value is negative.
 */
fun Long.toRupeeString(): String = UpiAmountFormatter.formatPaiseToRupees(this)

/**
 * Converts this Long value to a plain rupee decimal string without currency symbol.
 *
 * Delegates to [UpiAmountFormatter.paiseToRupeeString].
 *
 * Example: `10050L.toPlainRupeeString()` → `"100.50"`
 *
 * @return Plain decimal rupee string with 2 decimal places.
 * @throws IllegalArgumentException if this value is negative.
 */
fun Long.toPlainRupeeString(): String = UpiAmountFormatter.paiseToRupeeString(this)

/**
 * Parses this String (representing a rupee amount) to paise.
 *
 * Delegates to [UpiAmountFormatter.parseRupeesToPaise].
 *
 * Example: `"100.50".toPaise()` → `10050L`
 *
 * @return Amount in paise, or `null` if this string cannot be parsed.
 */
fun String.toPaise(): Long? = UpiAmountFormatter.parseRupeesToPaise(this)
