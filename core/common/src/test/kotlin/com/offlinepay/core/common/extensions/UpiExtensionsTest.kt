package com.offlinepay.core.common.extensions

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Unit tests for UPI extension functions in [UpiExtensions.kt].
 *
 * Covers: [String.isValidUpiId], [Long.toRupeeString], [Long.toPlainRupeeString],
 * [String.toPaise] — including NPCI UPI ID format edge cases.
 */
class UpiExtensionsTest {

    // ── isValidUpiId — valid cases ────────────────────────────────────────────

    @ParameterizedTest(name = "\"{0}\" should be a valid UPI ID")
    @ValueSource(strings = [
        "merchant@upi",
        "john.doe@okaxis",
        "9876543210@jio",
        "shop-name_123@icici",
        "user@paytm",
        "a@abc",
        "test.user123@upi",
        "MERCHANT@UPI",
    ])
    fun `valid UPI IDs are recognized`(upiId: String) {
        upiId.isValidUpiId() shouldBe true
    }

    // ── isValidUpiId — invalid cases ──────────────────────────────────────────

    @ParameterizedTest(name = "\"{0}\" should NOT be a valid UPI ID")
    @ValueSource(strings = [
        "",
        "   ",
        "nohsymbol",
        "@nolocal",
        "missing@",
        "two@@signs",
        "space in@upi",
        "local@ab",        // handle too short (< 3 chars)
        "local@123",       // handle must be letters only
        "local@ handle",   // space in handle
    ])
    fun `invalid UPI IDs are rejected`(upiId: String) {
        upiId.isValidUpiId() shouldBe false
    }

    @Test
    fun `isValidUpiId returns false for string exceeding 256 chars`() {
        // 253 'a' chars + "@upi" = 257 total chars — exceeds MAX_UPI_ID_LENGTH (256)
        val longId = "a".repeat(253) + "@upi"
        longId.length shouldBe 257
        longId.isValidUpiId() shouldBe false
    }

    @Test
    fun `isValidUpiId returns false for null-like blank string`() {
        "  ".isValidUpiId() shouldBe false
    }

    // ── toRupeeString ─────────────────────────────────────────────────────────

    @Test
    fun `toRupeeString converts paise to rupee display string`() {
        10050L.toRupeeString() shouldBe "₹100.50"
    }

    @Test
    fun `toRupeeString converts zero to rupee zero`() {
        0L.toRupeeString() shouldBe "₹0.00"
    }

    // ── toPlainRupeeString ────────────────────────────────────────────────────

    @Test
    fun `toPlainRupeeString returns decimal without symbol`() {
        500L.toPlainRupeeString() shouldBe "5.00"
    }

    // ── toPaise ───────────────────────────────────────────────────────────────

    @Test
    fun `toPaise parses valid rupee string`() {
        "100.50".toPaise() shouldBe 10050L
    }

    @Test
    fun `toPaise returns null for invalid string`() {
        "not-a-number".toPaise() shouldBe null
    }

    @Test
    fun `toPaise returns null for empty string`() {
        "".toPaise() shouldBe null
    }
}
