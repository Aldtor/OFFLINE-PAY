package com.offlinepay.core.common.time

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Utility object for epoch millisecond ↔ display string conversions used throughout OfflinePay.
 *
 * All timestamps are stored as epoch milliseconds (Long) in the Room database.
 * This object provides consistent, locale-aware formatting for display in the UI.
 *
 * Design reference: Section 3.5 (`:core:common`)
 * Requirements: Req 8.2 (timestamp stored in TransactionRecord)
 */
object DateTimeUtils {

    /** Full date and time format: "26 Jun 2025, 10:30 AM" */
    private const val FULL_DATE_TIME_PATTERN = "dd MMM yyyy, hh:mm a"

    /** Short date format: "26 Jun 2025" */
    private const val SHORT_DATE_PATTERN = "dd MMM yyyy"

    /** Time-only format: "10:30 AM" */
    private const val TIME_ONLY_PATTERN = "hh:mm a"

    /** ISO date for CSV export: "2025-06-26" */
    private const val ISO_DATE_PATTERN = "yyyy-MM-dd"

    /** ISO date-time for CSV export: "2025-06-26T10:30:00" */
    private const val ISO_DATE_TIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss"

    private val DISPLAY_LOCALE = Locale.ENGLISH

    /**
     * Formats an epoch millisecond timestamp as a full display string.
     *
     * Example: `1750000000000L` → `"26 Jun 2025, 10:30 AM"`
     *
     * @param epochMs Epoch milliseconds. Must be > 0.
     * @return Formatted date-time string, or empty string if [epochMs] ≤ 0.
     */
    fun formatFullDateTime(epochMs: Long): String {
        if (epochMs <= 0L) return ""
        return SimpleDateFormat(FULL_DATE_TIME_PATTERN, DISPLAY_LOCALE)
            .format(Date(epochMs))
    }

    /**
     * Formats an epoch millisecond timestamp as a short date string.
     *
     * Example: `1750000000000L` → `"26 Jun 2025"`
     *
     * @param epochMs Epoch milliseconds. Must be > 0.
     * @return Formatted date string, or empty string if [epochMs] ≤ 0.
     */
    fun formatShortDate(epochMs: Long): String {
        if (epochMs <= 0L) return ""
        return SimpleDateFormat(SHORT_DATE_PATTERN, DISPLAY_LOCALE)
            .format(Date(epochMs))
    }

    /**
     * Formats an epoch millisecond timestamp as a time-only string.
     *
     * Example: `1750000000000L` → `"10:30 AM"`
     *
     * @param epochMs Epoch milliseconds. Must be > 0.
     * @return Formatted time string, or empty string if [epochMs] ≤ 0.
     */
    fun formatTimeOnly(epochMs: Long): String {
        if (epochMs <= 0L) return ""
        return SimpleDateFormat(TIME_ONLY_PATTERN, DISPLAY_LOCALE)
            .format(Date(epochMs))
    }

    /**
     * Formats an epoch millisecond timestamp as an ISO date string for CSV export.
     *
     * Example: `1750000000000L` → `"2025-06-26"`
     *
     * @param epochMs Epoch milliseconds. Must be > 0.
     * @return ISO date string, or empty string if [epochMs] ≤ 0.
     */
    fun formatIsoDate(epochMs: Long): String {
        if (epochMs <= 0L) return ""
        return SimpleDateFormat(ISO_DATE_PATTERN, DISPLAY_LOCALE)
            .format(Date(epochMs))
    }

    /**
     * Formats an epoch millisecond timestamp as an ISO date-time string for CSV export.
     *
     * Example: `1750000000000L` → `"2025-06-26T10:30:00"`
     *
     * @param epochMs Epoch milliseconds. Must be > 0.
     * @return ISO date-time string, or empty string if [epochMs] ≤ 0.
     */
    fun formatIsoDateTime(epochMs: Long): String {
        if (epochMs <= 0L) return ""
        return SimpleDateFormat(ISO_DATE_TIME_PATTERN, DISPLAY_LOCALE)
            .format(Date(epochMs))
    }

    /**
     * Returns a relative time display string for the dashboard "last payment" summary.
     *
     * Examples:
     * - < 1 minute ago → `"Just now"`
     * - < 60 minutes ago → `"5 minutes ago"`
     * - < 24 hours ago → `"3 hours ago"`
     * - < 7 days ago → `"2 days ago"`
     * - Otherwise → formatted short date via [formatShortDate]
     *
     * Design reference: Section 10.2 (DashboardViewModel — last payment relative timestamp)
     *
     * @param epochMs The timestamp of the event. Must be > 0.
     * @param nowMs The current time in epoch milliseconds (defaults to [System.currentTimeMillis]).
     *   Inject for deterministic testing.
     * @return Human-readable relative time string.
     */
    fun relativeTimeString(epochMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        if (epochMs <= 0L) return ""
        val diffMs = nowMs - epochMs
        if (diffMs < 0L) return formatShortDate(epochMs) // future timestamp — show date
        val diffMinutes = TimeUnit.MILLISECONDS.toMinutes(diffMs)
        val diffHours = TimeUnit.MILLISECONDS.toHours(diffMs)
        val diffDays = TimeUnit.MILLISECONDS.toDays(diffMs)
        return when {
            diffMinutes < 1L -> "Just now"
            diffMinutes == 1L -> "1 minute ago"
            diffMinutes < 60L -> "$diffMinutes minutes ago"
            diffHours == 1L -> "1 hour ago"
            diffHours < 24L -> "$diffHours hours ago"
            diffDays == 1L -> "Yesterday"
            diffDays < 7L -> "$diffDays days ago"
            else -> formatShortDate(epochMs)
        }
    }

    /**
     * Returns the epoch millisecond timestamp for the start of the current day (00:00:00.000).
     *
     * Used by [TransactionDao.getToday] to query today's transactions.
     *
     * @param nowMs The current time in epoch milliseconds (defaults to [System.currentTimeMillis]).
     * @return Epoch milliseconds at the start of today.
     */
    fun startOfDayMs(nowMs: Long = System.currentTimeMillis()): Long {
        val date = Date(nowMs)
        val cal = java.util.Calendar.getInstance().apply {
            time = date
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    /**
     * Returns the epoch millisecond timestamp for the start of the current month (day 1, 00:00:00.000).
     *
     * Used by [TransactionDao.getThisMonth] to query this month's transactions.
     *
     * @param nowMs The current time in epoch milliseconds (defaults to [System.currentTimeMillis]).
     * @return Epoch milliseconds at the start of the current month.
     */
    fun startOfMonthMs(nowMs: Long = System.currentTimeMillis()): Long {
        val date = Date(nowMs)
        val cal = java.util.Calendar.getInstance().apply {
            time = date
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    /**
     * Returns the epoch millisecond timestamp 90 days before [nowMs].
     *
     * Used by the retention worker to delete transactions older than 90 days (Req 8.12).
     *
     * @param nowMs The current time in epoch milliseconds (defaults to [System.currentTimeMillis]).
     * @return Epoch milliseconds 90 days prior.
     */
    fun ninetyDaysAgoMs(nowMs: Long = System.currentTimeMillis()): Long =
        nowMs - TimeUnit.DAYS.toMillis(90L)
}
