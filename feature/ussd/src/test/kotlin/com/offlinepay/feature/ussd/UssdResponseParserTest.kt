package com.offlinepay.feature.ussd

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Unit tests for [UssdResponseParser].
 *
 * Verifies USSD response classification across all categories:
 * Success, BankMenu, PinPrompt, BankError, Unknown.
 * Also verifies PII (UPI ID, amounts, phone patterns) are not present in sanitised output.
 *
 * Design reference: Section 6.3, Section 16.1
 * Requirements: Req 5.8 (never read/store UPI PIN)
 */
class UssdResponseParserTest {

    private val parser = UssdResponseParser()

    // ── Success responses ──────────────────────────────────────────────────────

    @Test
    fun `parse success response with transaction ID`() {
        val response = "Payment of Rs 500.00 to merchant@upi successful. Txn ID: UPI123456789"
        val result = parser.parseUssdResponse(response)
        result.classification shouldBe UssdResponseClassification.SUCCESS
    }

    @Test
    fun `parse success response with confirmed keyword`() {
        val response = "Your UPI payment of INR 100.00 has been confirmed"
        val result = parser.parseUssdResponse(response)
        result.classification shouldBe UssdResponseClassification.SUCCESS
    }

    @Test
    fun `parse success response with approved keyword`() {
        val response = "Transaction approved. Ref No: 123456"
        val result = parser.parseUssdResponse(response)
        result.classification shouldBe UssdResponseClassification.SUCCESS
    }

    // ── Bank menu responses ────────────────────────────────────────────────────

    @Test
    fun `parse bank menu response with numbered options`() {
        val response = "Select your bank:\n1. SBI\n2. PNB\n3. HDFC\n4. ICICI"
        val result = parser.parseUssdResponse(response)
        result.classification shouldBe UssdResponseClassification.BANK_MENU
    }

    @Test
    fun `parse bank menu response with reply keyword`() {
        val response = "Reply with option number:\n1. Send Money\n2. Request Money"
        val result = parser.parseUssdResponse(response)
        result.classification shouldBe UssdResponseClassification.BANK_MENU
    }

    // ── PIN prompt responses ───────────────────────────────────────────────────

    @Test
    fun `parse PIN prompt response`() {
        val response = "Enter your UPI PIN to complete the transaction"
        val result = parser.parseUssdResponse(response)
        result.classification shouldBe UssdResponseClassification.PIN_PROMPT
    }

    @Test
    fun `parse MPIN prompt response`() {
        val response = "Please enter your MPIN"
        val result = parser.parseUssdResponse(response)
        result.classification shouldBe UssdResponseClassification.PIN_PROMPT
    }

    // ── Bank error responses ───────────────────────────────────────────────────

    @Test
    fun `parse declined response`() {
        val response = "Transaction declined by bank. Insufficient funds."
        val result = parser.parseUssdResponse(response)
        result.classification shouldBe UssdResponseClassification.BANK_ERROR
    }

    @Test
    fun `parse failed response`() {
        val response = "Transaction failed. Please try again later."
        val result = parser.parseUssdResponse(response)
        result.classification shouldBe UssdResponseClassification.BANK_ERROR
    }

    @Test
    fun `parse error with insufficient funds`() {
        val response = "Insufficient balance in your account"
        val result = parser.parseUssdResponse(response)
        result.classification shouldBe UssdResponseClassification.BANK_ERROR
    }

    // ── Unknown responses ──────────────────────────────────────────────────────

    @Test
    fun `parse unknown response for unrecognised text`() {
        val response = "Some random text that doesn't match any pattern"
        val result = parser.parseUssdResponse(response)
        result.classification shouldBe UssdResponseClassification.UNKNOWN
    }

    @Test
    fun `parse empty response as unknown`() {
        val result = parser.parseUssdResponse("")
        result.classification shouldBe UssdResponseClassification.UNKNOWN
    }

    // ── PII sanitisation ───────────────────────────────────────────────────────

    @Test
    fun `sanitised output does not contain UPI ID patterns`() {
        val response = "Payment to merchant@upi successful"
        val result = parser.parseUssdResponse(response)
        val sanitised = result.sanitisedResponse
        sanitised.contains("merchant@upi") shouldBe false
    }

    @Test
    fun `sanitised output does not contain phone number patterns`() {
        val response = "Payment from 9876543210 successful"
        val result = parser.parseUssdResponse(response)
        val sanitised = result.sanitisedResponse
        sanitised.contains("9876543210") shouldBe false
    }

    @Test
    fun `sanitised output does not contain amount patterns`() {
        val response = "Payment of Rs 500.00 successful"
        val result = parser.parseUssdResponse(response)
        val sanitised = result.sanitisedResponse
        sanitised.contains("500.00") shouldBe false
    }
}
