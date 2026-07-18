package com.offlinepay.core.common.format

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Unit tests for [UpiAmountFormatter].
 *
 * Covers: paise→rupee conversion, rupee string parsing, validation rules,
 * boundary values, and NPCI limit enforcement.
 */
class UpiAmountFormatterTest {

    // ── formatPaiseToRupees ───────────────────────────────────────────────────

    @Test
    fun `formatPaiseToRupees converts 100 paise to 1 rupee`() {
        val result = UpiAmountFormatter.formatPaiseToRupees(100L)
        result shouldBe "₹1.00"
    }

    @Test
    fun `formatPaiseToRupees converts 10050 paise to 100 rupees 50 paise`() {
        val result = UpiAmountFormatter.formatPaiseToRupees(10050L)
        result shouldBe "₹100.50"
    }

    @Test
    fun `formatPaiseToRupees converts zero paise to rupee zero`() {
        val result = UpiAmountFormatter.formatPaiseToRupees(0L)
        result shouldBe "₹0.00"
    }

    @Test
    fun `formatPaiseToRupees formats NPCI max amount correctly`() {
        // ₹1,00,000.00 = 10,000,000 paise
        // Indian locale groups as "1,00,000" but some JVM versions output "100,000"
        // Both are valid — we verify the amount value is correct by round-tripping
        val result = UpiAmountFormatter.formatPaiseToRupees(10_000_000L)
        result.startsWith("₹") shouldBe true
        result.contains("100000".filter { it.isDigit() }) shouldBe false  // sanity
        // Round-trip: strip symbol and parse back
        val parsed = UpiAmountFormatter.parseRupeesToPaise(result)
        parsed shouldBe 10_000_000L
    }

    @Test
    fun `formatPaiseToRupees throws for negative amount`() {
        shouldThrow<IllegalArgumentException> {
            UpiAmountFormatter.formatPaiseToRupees(-100L)
        }
    }

    // ── paiseToRupeeString ────────────────────────────────────────────────────

    @Test
    fun `paiseToRupeeString returns plain decimal without symbol`() {
        UpiAmountFormatter.paiseToRupeeString(10050L) shouldBe "100.50"
    }

    @Test
    fun `paiseToRupeeString returns 0 dot 00 for zero`() {
        UpiAmountFormatter.paiseToRupeeString(0L) shouldBe "0.00"
    }

    @Test
    fun `paiseToRupeeString throws for negative amount`() {
        shouldThrow<IllegalArgumentException> {
            UpiAmountFormatter.paiseToRupeeString(-1L)
        }
    }

    // ── parseRupeesToPaise ────────────────────────────────────────────────────

    @ParameterizedTest(name = "parseRupeesToPaise(\"{0}\") = {1}")
    @CsvSource(
        "100, 10000",
        "100.50, 10050",
        "100.5, 10050",
        "1, 100",
        "0, 0",
        "1000000, 100000000",
    )
    fun `parseRupeesToPaise parses valid rupee strings`(input: String, expected: Long) {
        UpiAmountFormatter.parseRupeesToPaise(input) shouldBe expected
    }

    @Test
    fun `parseRupeesToPaise strips rupee symbol`() {
        UpiAmountFormatter.parseRupeesToPaise("₹500.00") shouldBe 50000L
    }

    @Test
    fun `parseRupeesToPaise strips Indian number commas`() {
        UpiAmountFormatter.parseRupeesToPaise("1,00,000") shouldBe 10_000_000L
    }

    @Test
    fun `parseRupeesToPaise returns null for non-numeric string`() {
        UpiAmountFormatter.parseRupeesToPaise("abc") shouldBe null
    }

    @Test
    fun `parseRupeesToPaise returns null for empty string`() {
        UpiAmountFormatter.parseRupeesToPaise("") shouldBe null
    }

    @Test
    fun `parseRupeesToPaise returns null for negative value`() {
        UpiAmountFormatter.parseRupeesToPaise("-100") shouldBe null
    }

    // ── validate ─────────────────────────────────────────────────────────────

    @Test
    fun `validate returns Valid for amount within range`() {
        UpiAmountFormatter.validate(10000L).shouldBeInstanceOf<UpiAmountFormatter.AmountValidationResult.Valid>()
    }

    @Test
    fun `validate returns Valid for minimum allowed paise`() {
        UpiAmountFormatter.validate(100L)
            .shouldBeInstanceOf<UpiAmountFormatter.AmountValidationResult.Valid>()
    }

    @Test
    fun `validate returns Valid for maximum allowed paise`() {
        UpiAmountFormatter.validate(10_000_000L)
            .shouldBeInstanceOf<UpiAmountFormatter.AmountValidationResult.Valid>()
    }

    @Test
    fun `validate returns Zero for zero paise`() {
        UpiAmountFormatter.validate(0L)
            .shouldBeInstanceOf<UpiAmountFormatter.AmountValidationResult.Zero>()
    }

    @Test
    fun `validate returns Zero for negative paise`() {
        UpiAmountFormatter.validate(-100L)
            .shouldBeInstanceOf<UpiAmountFormatter.AmountValidationResult.Zero>()
    }

    @Test
    fun `validate returns BelowMinimum for 1 paise`() {
        val result = UpiAmountFormatter.validate(1L)
        result.shouldBeInstanceOf<UpiAmountFormatter.AmountValidationResult.BelowMinimum>()
        (result as UpiAmountFormatter.AmountValidationResult.BelowMinimum).minimumPaise shouldBe 100L
    }

    @Test
    fun `validate returns ExceedsMaximum for amount above NPCI limit`() {
        val result = UpiAmountFormatter.validate(10_000_001L)
        result.shouldBeInstanceOf<UpiAmountFormatter.AmountValidationResult.ExceedsMaximum>()
        (result as UpiAmountFormatter.AmountValidationResult.ExceedsMaximum).maximumPaise shouldBe 10_000_000L
    }

    // ── Round-trip ────────────────────────────────────────────────────────────

    @Test
    fun `round-trip paise to rupee string and back`() {
        val originalPaise = 7350L
        val rupeeString = UpiAmountFormatter.paiseToRupeeString(originalPaise)
        val parsedPaise = UpiAmountFormatter.parseRupeesToPaise(rupeeString)
        parsedPaise shouldBe originalPaise
    }
}
