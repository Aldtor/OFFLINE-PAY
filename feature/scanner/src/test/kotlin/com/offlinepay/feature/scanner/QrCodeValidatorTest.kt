package com.offlinepay.feature.scanner

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Unit tests for [QrCodeValidator].
 *
 * Tests all valid and invalid UPI QR URI formats, edge cases,
 * and non-UPI QR code rejection.
 *
 * Design reference: Section 5.4 (QR parsing)
 * Requirements: Req 3.1–3.9
 */
class QrCodeValidatorTest {

    private val validator = QrCodeValidator()

    // ── Valid UPI QRs ──────────────────────────────────────────────────────────

    @Nested
    inner class ValidQrCodes {

        @Test
        fun `valid UPI QR with minimal fields`() {
            val uri = "upi://pay?pa=merchant@upi&pn=Merchant"
            val result = validator.validate(uri)
            result.shouldBeInstanceOf<QrValidationResult.Valid>()
            (result as QrValidationResult.Valid).data.payeeUpiId shouldBe "merchant@upi"
            result.data.payeeName shouldBe "Merchant"
        }

        @Test
        fun `valid UPI QR with all fields`() {
            val uri = "upi://pay?pa=shop@ybl&pn=SuperShop&am=500.00&cu=INR&tn=Purchase&mc=5411&tr=TXN12345"
            val result = validator.validate(uri)
            result.shouldBeInstanceOf<QrValidationResult.Valid>()
            val data = (result as QrValidationResult.Valid).data
            data.payeeUpiId shouldBe "shop@ybl"
            data.payeeName shouldBe "SuperShop"
            data.amountPaise shouldBe 50000L
            data.merchantCategoryCode shouldBe "5411"
            data.transactionReference shouldBe "TXN12345"
        }

        @Test
        fun `valid UPI QR with URL-encoded name`() {
            val uri = "upi://pay?pa=merchant@upi&pn=Super%20Shop%20%26%20Store"
            val result = validator.validate(uri)
            result.shouldBeInstanceOf<QrValidationResult.Valid>()
            (result as QrValidationResult.Valid).data.payeeName shouldBe "Super Shop & Store"
        }

        @Test
        fun `valid UPI QR with zero amount`() {
            val uri = "upi://pay?pa=merchant@upi&pn=Merchant&am=0"
            val result = validator.validate(uri)
            result.shouldBeInstanceOf<QrValidationResult.Valid>()
            (result as QrValidationResult.Valid).data.amountPaise shouldBe null
        }

        @ParameterizedTest(name = "valid UPI ID format: {0}")
        @ValueSource(strings = [
            "merchant@upi",
            "shop@ybl",
            "pay.merchant.123@sbi",
            "merchant@paytm",
            "user-123@icici",
        ])
        fun `accepts valid UPI ID formats`(upiId: String) {
            val uri = "upi://pay?pa=$upiId&pn=Test"
            val result = validator.validate(uri)
            result.shouldBeInstanceOf<QrValidationResult.Valid>()
        }
    }

    // ── Invalid UPI QRs ────────────────────────────────────────────────────────

    @Nested
    inner class InvalidQrCodes {

        @Test
        fun `rejects empty string`() {
            val result = validator.validate("")
            result.shouldBeInstanceOf<QrValidationResult.Invalid>()
        }

        @Test
        fun `rejects non-UPI URI`() {
            val result = validator.validate("https://example.com")
            result.shouldBeInstanceOf<QrValidationResult.NonUpi>()
        }

        @Test
        fun `rejects UPI URI without pa field`() {
            val result = validator.validate("upi://pay?pn=Merchant&am=100")
            result.shouldBeInstanceOf<QrValidationResult.Invalid>()
        }

        @Test
        fun `rejects UPI URI with empty pa field`() {
            val result = validator.validate("upi://pay?pa=&pn=Merchant")
            result.shouldBeInstanceOf<QrValidationResult.Invalid>()
        }

        @Test
        fun `rejects UPI ID without @ symbol`() {
            val result = validator.validate("upi://pay?pa=invalidupiid&pn=Merchant")
            result.shouldBeInstanceOf<QrValidationResult.Invalid>()
        }

        @Test
        fun `rejects negative amount`() {
            val result = validator.validate("upi://pay?pa=merchant@upi&pn=Merchant&am=-100")
            result.shouldBeInstanceOf<QrValidationResult.Invalid>()
        }

        @Test
        fun `rejects amount above NPCI limit`() {
            val result = validator.validate("upi://pay?pa=merchant@upi&pn=Merchant&am=200000")
            result.shouldBeInstanceOf<QrValidationResult.Invalid>()
        }

        @ParameterizedTest(name = "rejects non-UPI scheme: {0}")
        @ValueSource(strings = [
            "http://example.com",
            "tel:1234567890",
            "sms:hello",
            "random text",
            "1234567890",
        ])
        fun `rejects non-UPI schemes`(input: String) {
            val result = validator.validate(input)
            result.shouldBeInstanceOf<QrValidationResult.NonUpi>()
        }
    }

    // ── Edge cases ─────────────────────────────────────────────────────────────

    @Nested
    inner class EdgeCases {

        @Test
        fun `handles case-insensitive scheme`() {
            val uri = "UPI://PAY?pa=merchant@upi&pn=Merchant"
            val result = validator.validate(uri)
            result.shouldBeInstanceOf<QrValidationResult.Valid>()
        }

        @Test
        fun `handles extra whitespace in URI`() {
            val uri = "  upi://pay?pa=merchant@upi&pn=Merchant  "
            val result = validator.validate(uri)
            result.shouldBeInstanceOf<QrValidationResult.Valid>()
        }

        @Test
        fun `preserves original UPI ID casing`() {
            val uri = "upi://pay?pa=Merchant@UPI&pn=Merchant"
            val result = validator.validate(uri)
            result.shouldBeInstanceOf<QrValidationResult.Valid>()
        }
    }
}
