package com.offlinepay.core.common.format

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/**
 * Formatter for UPI payment amounts used throughout OfflinePay.
 *
 * OfflinePay stores all monetary amounts as **paise** (Long) internally to avoid
 * floating-point rounding errors in financial calculations. This formatter handles
 * conversion between paise and display rupee strings with Indian locale formatting.
 *
 * ### Paise convention
 * - ₹1.00 = 100 paise
 * - ₹100.50 = 10,050 paise
 * - ₹1,00,000 (NPCI max) = 1,00,00,000 paise = 10,000,000 paise
 *
 * ### NPCI limits
 * - Minimum: ₹1.00 (100 paise) — payments below this are rejected
 * - Maximum: ₹1,00,000.00 (10,000,000 paise) — NPCI *99# USSD limit
 *
 * Design reference: Section 3.5 (`:core:common`), Section 9.1 (amount stored as Long paise)
 * Requirements: Req 7.3 (amount validation)
 */
object UpiAmountFormatter {

    /** Minimum transaction amount in paise (₹1.00). */
    const val MIN_AMOUNT_PAISE: Long = 100L

    /** Maximum transaction amount in paise (₹1,00,000.00) — NPCI *99# limit. */
    const val MAX_AMOUNT_PAISE: Long = 10_000_000L

    private val INDIAN_LOCALE = Locale("en", "IN")

    /**
     * Converts a paise amount to a display-friendly rupee string with Indian locale formatting.
     *
     * Examples:
     * - `100L` → `"₹1.00"`
     * - `10050L` → `"₹100.50"`
     * - `10_000_000L` → `"₹1,00,000.00"`
     * - `0L` → `"₹0.00"`
     *
     * @param paise The amount in paise. Must be ≥ 0.
     * @return A formatted rupee string with the ₹ symbol and 2 decimal places.
     * @throws IllegalArgumentException if [paise] is negative.
     */
    fun formatPaiseToRupees(paise: Long): String {
        require(paise >= 0L) { "Amount in paise cannot be negative: $paise" }
        val rupees = BigDecimal(paise).divide(BigDecimal(100), 2, RoundingMode.UNNECESSARY)
        val formatter = NumberFormat.getCurrencyInstance(INDIAN_LOCALE)
        formatter.minimumFractionDigits = 2
        formatter.maximumFractionDigits = 2
        return formatter.format(rupees)
    }

    /**
     * Converts a paise amount to a plain rupee string without currency symbol.
     *
     * Useful for input fields and CSV export.
     *
     * Examples:
     * - `100L` → `"1.00"`
     * - `10050L` → `"100.50"`
     * - `0L` → `"0.00"`
     *
     * @param paise The amount in paise. Must be ≥ 0.
     * @return A plain decimal string with 2 decimal places.
     * @throws IllegalArgumentException if [paise] is negative.
     */
    fun paiseToRupeeString(paise: Long): String {
        require(paise >= 0L) { "Amount in paise cannot be negative: $paise" }
        val rupees = BigDecimal(paise).divide(BigDecimal(100), 2, RoundingMode.UNNECESSARY)
        return rupees.toPlainString()
    }

    /**
     * Parses a rupee string (user-entered or from a UPI QR `am` field) to paise.
     *
     * Accepts formats: `"100"`, `"100.50"`, `"100.5"`, `"1,00,000"`, `"1,00,000.00"`.
     * Strips commas and the ₹ symbol before parsing.
     *
     * @param rupeeString The rupee string to parse.
     * @return The amount in paise, or null if the string cannot be parsed.
     */
    fun parseRupeesToPaise(rupeeString: String): Long? {
        val cleaned = rupeeString
            .trim()
            .replace("₹", "")
            .replace(",", "")
            .replace(" ", "")
        return try {
            val decimal = BigDecimal(cleaned).setScale(2, RoundingMode.HALF_UP)
            val paise = decimal.multiply(BigDecimal(100)).toLong()
            if (paise < 0) null else paise
        } catch (_: NumberFormatException) {
            null
        } catch (_: ArithmeticException) {
            null
        }
    }

    /**
     * Validates whether a paise amount is within NPCI's allowed transaction range.
     *
     * - Must be > 0 (zero is not allowed per Req 7.3)
     * - Must be ≥ [MIN_AMOUNT_PAISE] (₹1.00)
     * - Must be ≤ [MAX_AMOUNT_PAISE] (₹1,00,000.00)
     *
     * @param paise The amount in paise to validate.
     * @return [AmountValidationResult] describing the validation outcome.
     */
    fun validate(paise: Long): AmountValidationResult = when {
        paise <= 0L -> AmountValidationResult.Zero
        paise < MIN_AMOUNT_PAISE -> AmountValidationResult.BelowMinimum(MIN_AMOUNT_PAISE)
        paise > MAX_AMOUNT_PAISE -> AmountValidationResult.ExceedsMaximum(MAX_AMOUNT_PAISE)
        else -> AmountValidationResult.Valid
    }

    /**
     * Result of amount validation per Req 7.3 and NPCI rules.
     */
    sealed class AmountValidationResult {
        /** Amount is within the valid range. */
        data object Valid : AmountValidationResult()

        /** Amount is zero or negative (not allowed). */
        data object Zero : AmountValidationResult()

        /** Amount is positive but below the minimum threshold. */
        data class BelowMinimum(val minimumPaise: Long) : AmountValidationResult()

        /** Amount exceeds the NPCI maximum limit. */
        data class ExceedsMaximum(val maximumPaise: Long) : AmountValidationResult()
    }
}
