package com.offlinepay.feature.ussd

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Semantic classification of a raw USSD response string.
 *
 * Design reference: Section 5.4 (response classification)
 * Requirements: Req 5.5 (parse USSD response), Req 5.6 (PII sanitisation)
 */
enum class UssdResponseType {
    /** Payment completed successfully. */
    SUCCESS,

    /** Intermediate menu — user must select an option. */
    BANK_MENU,

    /** PIN entry prompt — must NEVER be displayed or logged. */
    PIN_PROMPT,

    /** Bank reported an error (insufficient funds, declined, etc.). */
    BANK_ERROR,

    /** Response does not match any known pattern. */
    UNKNOWN,
}

/**
 * The result of parsing a raw USSD response.
 *
 * @param type     Semantic classification of the response.
 * @param sanitisedText Raw text with all PII tokens replaced by safe placeholders.
 *                      Safe to display in the UI and write to analytics/logs.
 */
data class ParsedUssdResponse(
    val type: UssdResponseType,
    val sanitisedText: String,
)

/**
 * Parses and sanitises raw USSD response strings.
 *
 * **Security contract**: the raw response MUST NOT be displayed in the UI,
 * stored in the database, or sent to analytics. Always use [sanitise] or
 * [parseUssdResponse] before doing anything with a USSD string.
 *
 * Design reference: Section 5.4 (response parser), Section 5.6 (PII scrubbing)
 * Requirements: Req 5.5 (parse), Req 5.6 (sanitise), Req 5.8 (PIN prompt gate)
 */
@Singleton
class UssdResponseParser @Inject constructor() {

    // PII patterns — strip before any display or persistence
    private val upiIdPattern    = Regex("[a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z]{2,64}")
    private val phonePattern    = Regex("\\b[6-9]\\d{9}\\b")
    private val amountPattern   = Regex("(?i)(rs\\.?|inr|₹)\\s*[\\d,]+(\\.\\d{1,2})?")

    /**
     * Classifies and sanitises [rawResponse].
     *
     * @return [ParsedUssdResponse] whose [ParsedUssdResponse.sanitisedText] is safe
     *         for UI display, logs, and database storage.
     */
    fun parseUssdResponse(rawResponse: String): ParsedUssdResponse {
        val sanitised = sanitise(rawResponse)
        val type      = classify(rawResponse.lowercase())
        return ParsedUssdResponse(type, sanitised)
    }

    /**
     * Returns a copy of [text] with all detectable PII replaced by labelled
     * placeholders.  Idempotent — calling twice produces the same output.
     */
    fun sanitise(text: String): String = text
        .replace(upiIdPattern,   "[REDACTED_UPI]")
        .replace(phonePattern,   "[REDACTED_PHONE]")
        .replace(amountPattern,  "[REDACTED_AMOUNT]")

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun classify(lower: String): UssdResponseType = when {
        // PIN prompt — checked first so it cannot be misclassified as a menu
        lower.contains("enter.*pin".toRegex()) ||
            lower.contains("upi pin") ||
            lower.contains("mpin")                              -> UssdResponseType.PIN_PROMPT

        // Bank-side errors — checked BEFORE success so a failure message that also
        // contains a success keyword (e.g. "Transaction failed - not credited to
        // beneficiary" or "Insufficient balance, could not be debited") is not
        // misclassified as SUCCESS.
        lower.contains("fail") || lower.contains("declined") ||
            lower.contains("insufficient") || lower.contains("error") ||
            lower.contains("invalid")                           -> UssdResponseType.BANK_ERROR

        // Success indicators
        lower.contains("success") || lower.contains("successful") ||
            lower.contains("approved") || lower.contains("credited") ||
            lower.contains("debited")                           -> UssdResponseType.SUCCESS

        // Bank menu / navigation prompts
        lower.contains("press 1") || lower.contains("enter 1") ||
            lower.contains("select") || lower.contains("menu") -> UssdResponseType.BANK_MENU

        else                                                    -> UssdResponseType.UNKNOWN
    }
}
