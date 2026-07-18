package com.offlinepay.core.common.time

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.string.shouldNotBeEmpty
import org.junit.jupiter.api.Test

/**
 * Unit tests for [DateTimeUtils].
 *
 * Uses a fixed epoch timestamp for deterministic assertions.
 * 1750000000000L = approximately 2025-06-15T17:06:40 UTC
 */
class DateTimeUtilsTest {

    private val fixedEpochMs = 1_750_000_000_000L

    // ── formatFullDateTime ─────────────────────────────────────────────────────

    @Test
    fun `formatFullDateTime returns non-empty string for valid epoch`() {
        DateTimeUtils.formatFullDateTime(fixedEpochMs).shouldNotBeEmpty()
    }

    @Test
    fun `formatFullDateTime returns empty string for zero epoch`() {
        DateTimeUtils.formatFullDateTime(0L) shouldBe ""
    }

    @Test
    fun `formatFullDateTime returns empty string for negative epoch`() {
        DateTimeUtils.formatFullDateTime(-1L) shouldBe ""
    }

    // ── formatShortDate ───────────────────────────────────────────────────────

    @Test
    fun `formatShortDate returns non-empty string for valid epoch`() {
        DateTimeUtils.formatShortDate(fixedEpochMs).shouldNotBeEmpty()
    }

    @Test
    fun `formatShortDate returns empty for zero epoch`() {
        DateTimeUtils.formatShortDate(0L) shouldBe ""
    }

    // ── formatIsoDate ─────────────────────────────────────────────────────────

    @Test
    fun `formatIsoDate format matches yyyy-MM-dd pattern`() {
        val result = DateTimeUtils.formatIsoDate(fixedEpochMs)
        result shouldMatch Regex("\\d{4}-\\d{2}-\\d{2}")
    }

    // ── formatIsoDateTime ─────────────────────────────────────────────────────

    @Test
    fun `formatIsoDateTime format matches ISO pattern`() {
        val result = DateTimeUtils.formatIsoDateTime(fixedEpochMs)
        result shouldMatch Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")
    }

    // ── relativeTimeString ────────────────────────────────────────────────────

    @Test
    fun `relativeTimeString returns Just now within 60 seconds`() {
        val now = System.currentTimeMillis()
        DateTimeUtils.relativeTimeString(now - 30_000L, now) shouldBe "Just now"
    }

    @Test
    fun `relativeTimeString returns X minutes ago within an hour`() {
        val now = System.currentTimeMillis()
        val fiveMinutesAgo = now - 5 * 60 * 1000L
        DateTimeUtils.relativeTimeString(fiveMinutesAgo, now) shouldBe "5 minutes ago"
    }

    @Test
    fun `relativeTimeString returns 1 minute ago`() {
        val now = System.currentTimeMillis()
        val oneMinuteAgo = now - 60 * 1000L - 1L
        DateTimeUtils.relativeTimeString(oneMinuteAgo, now) shouldBe "1 minute ago"
    }

    @Test
    fun `relativeTimeString returns X hours ago within a day`() {
        val now = System.currentTimeMillis()
        val threeHoursAgo = now - 3 * 60 * 60 * 1000L
        DateTimeUtils.relativeTimeString(threeHoursAgo, now) shouldBe "3 hours ago"
    }

    @Test
    fun `relativeTimeString returns Yesterday for 1 day ago`() {
        val now = System.currentTimeMillis()
        val oneDayAgo = now - 24 * 60 * 60 * 1000L - 1L
        DateTimeUtils.relativeTimeString(oneDayAgo, now) shouldBe "Yesterday"
    }

    @Test
    fun `relativeTimeString returns X days ago within a week`() {
        val now = System.currentTimeMillis()
        val threeDaysAgo = now - 3 * 24 * 60 * 60 * 1000L
        DateTimeUtils.relativeTimeString(threeDaysAgo, now) shouldBe "3 days ago"
    }

    @Test
    fun `relativeTimeString returns formatted date for old timestamp`() {
        val now = System.currentTimeMillis()
        val tenDaysAgo = now - 10L * 24 * 60 * 60 * 1000L
        val result = DateTimeUtils.relativeTimeString(tenDaysAgo, now)
        result shouldMatch Regex("\\d{2} [A-Za-z]+ \\d{4}")
    }

    @Test
    fun `relativeTimeString returns empty string for zero epoch`() {
        DateTimeUtils.relativeTimeString(0L, System.currentTimeMillis()) shouldBe ""
    }

    // ── startOfDayMs / startOfMonthMs ─────────────────────────────────────────

    @Test
    fun `startOfDayMs is less than or equal to nowMs`() {
        val now = System.currentTimeMillis()
        (DateTimeUtils.startOfDayMs(now) <= now) shouldBe true
    }

    @Test
    fun `startOfMonthMs is less than or equal to nowMs`() {
        val now = System.currentTimeMillis()
        (DateTimeUtils.startOfMonthMs(now) <= now) shouldBe true
    }

    @Test
    fun `startOfMonthMs is less than or equal to startOfDayMs`() {
        val now = System.currentTimeMillis()
        (DateTimeUtils.startOfMonthMs(now) <= DateTimeUtils.startOfDayMs(now)) shouldBe true
    }

    // ── ninetyDaysAgoMs ───────────────────────────────────────────────────────

    @Test
    fun `ninetyDaysAgoMs is 90 days before now`() {
        val now = 1_750_000_000_000L
        val expected = now - 90L * 24 * 60 * 60 * 1000L
        DateTimeUtils.ninetyDaysAgoMs(now) shouldBe expected
    }
}
