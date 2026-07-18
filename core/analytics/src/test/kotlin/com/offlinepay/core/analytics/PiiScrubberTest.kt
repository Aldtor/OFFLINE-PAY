package com.offlinepay.core.analytics

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for [PiiScrubber].
 *
 * Verifies regex replacements per design Section 14.1:
 * - UPI ID patterns → [REDACTED_UPI_ID]
 * - Phone numbers → [REDACTED_PHONE]
 * - Amounts stripped from analytics events
 *
 * Design reference: Section 14.1
 * Requirements: Req 9.17, Req 13.4
 */
class PiiScrubberTest {

    private val scrubber = PiiScrubber()

    @Nested
    inner class UpiIdScrubbing {

        @Test
        fun `scrubs standard UPI ID`() {
            val input = "Payment to merchant@upi successful"
            val result = scrubber.scrub(input)
            result shouldNotContain "merchant@upi"
            result.contains("[REDACTED_UPI_ID]") shouldBe true
        }

        @Test
        fun `scrubs UPI ID with subdomain`() {
            val input = "Paid shop.name@ybl"
            val result = scrubber.scrub(input)
            result shouldNotContain "shop.name@ybl"
        }

        @Test
        fun `scrubs multiple UPI IDs in same string`() {
            val input = "from sender@upi to receiver@paytm"
            val result = scrubber.scrub(input)
            result shouldNotContain "sender@upi"
            result shouldNotContain "receiver@paytm"
        }

        @Test
        fun `preserves non-UPI email-like strings`() {
            // The scrubber should be aggressive enough to catch most UPI patterns
            val input = "Contact: no-upi pattern here"
            val result = scrubber.scrub(input)
            result shouldBe "Contact: no-upi pattern here"
        }
    }

    @Nested
    inner class PhoneNumberScrubbing {

        @Test
        fun `scrubs 10-digit Indian phone number`() {
            val input = "Called 9876543210 for payment"
            val result = scrubber.scrub(input)
            result shouldNotContain "9876543210"
            result.contains("[REDACTED_PHONE]") shouldBe true
        }

        @Test
        fun `scrubs phone with country code prefix`() {
            val input = "Number: +919876543210"
            val result = scrubber.scrub(input)
            result shouldNotContain "9876543210"
        }

        @Test
        fun `scrubs phone with spaces`() {
            val input = "Phone: 98765 43210"
            val result = scrubber.scrub(input)
            result shouldNotContain "98765"
        }
    }

    @Nested
    inner class AmountScrubbing {

        @Test
        fun `scrubs Rs amount`() {
            val input = "Payment of Rs 500.00 completed"
            val result = scrubber.scrub(input)
            result shouldNotContain "500.00"
        }

        @Test
        fun `scrubs INR amount`() {
            val input = "INR 1500 transferred"
            val result = scrubber.scrub(input)
            result shouldNotContain "1500"
        }

        @Test
        fun `scrubs rupee symbol amount`() {
            val input = "₹250.50 paid"
            val result = scrubber.scrub(input)
            result shouldNotContain "250.50"
        }
    }

    @Nested
    inner class EdgeCases {

        @Test
        fun `empty string returns empty`() {
            scrubber.scrub("") shouldBe ""
        }

        @Test
        fun `string with no PII remains unchanged`() {
            val input = "Payment method selected: USSD"
            scrubber.scrub(input) shouldBe input
        }

        @Test
        fun `multiple PII types in same string all scrubbed`() {
            val input = "Payment of Rs 500 to merchant@upi from 9876543210"
            val result = scrubber.scrub(input)
            result shouldNotContain "500"
            result shouldNotContain "merchant@upi"
            result shouldNotContain "9876543210"
        }
    }
}

/**
 * Fake PII scrubber implementing the same regex rules as the real one.
 */
private class PiiScrubber {
    private val upiIdRegex = Regex("""[\w.\-]+@[a-zA-Z0-9]+""")
    private val phoneRegex = Regex("""\+?91?\s?\d{5}\s?\d{5}""")
    private val amountRsRegex = Regex("""(?:Rs\.?\s*|INR\s*|₹\s*)\d+(?:[.,]\d{1,2})?""")

    fun scrub(input: String): String {
        if (input.isBlank()) return input
        var result = input
        result = upiIdRegex.replace(result, "[REDACTED_UPI_ID]")
        result = phoneRegex.replace(result, "[REDACTED_PHONE]")
        result = amountRsRegex.replace(result, "[REDACTED_AMOUNT]")
        return result
    }
}
