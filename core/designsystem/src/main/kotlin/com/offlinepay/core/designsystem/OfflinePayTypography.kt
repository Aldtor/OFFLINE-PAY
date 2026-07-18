package com.offlinepay.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * OfflinePay typography scale — Material 3 type system.
 *
 * Maps all M3 typography tokens from `displayLarge` (57sp) through `labelSmall` (11sp)
 * per design Section 10.3. Tokens not explicitly listed in the design table use the
 * standard Material 3 defaults with matching weight conventions.
 *
 * Design references:
 *   displayLarge  — 57sp Regular   — scan prompt ("Point at QR code")
 *   headlineMedium — 28sp SemiBold  — payment amount (₹500.00)
 *   headlineSmall  — 24sp SemiBold  — screen titles
 *   titleLarge     — 22sp Medium    — merchant name on confirmation
 *   bodyLarge      — 16sp Regular   — UPI ID, transaction details
 *   bodyMedium     — 14sp Regular   — list item secondary text
 *   labelLarge     — 14sp Medium    — button labels
 *   labelMedium    — 12sp Medium    — status badges, category tags
 *   labelSmall     — 11sp Regular   — timestamps, helper text
 */
val OfflinePayTypography = Typography(
    // ── Display ──────────────────────────────────────────────────────────────
    displayLarge = TextStyle(
        fontSize = 57.sp,
        lineHeight = 64.sp,
        fontWeight = FontWeight.Normal,      // Regular
        letterSpacing = (-0.25).sp,
    ),
    displayMedium = TextStyle(
        fontSize = 45.sp,
        lineHeight = 52.sp,
        fontWeight = FontWeight.Normal,      // Regular
        letterSpacing = 0.sp,
    ),
    displaySmall = TextStyle(
        fontSize = 36.sp,
        lineHeight = 44.sp,
        fontWeight = FontWeight.Normal,      // Regular
        letterSpacing = 0.sp,
    ),

    // ── Headline ─────────────────────────────────────────────────────────────
    headlineLarge = TextStyle(
        fontSize = 32.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontSize = 28.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.SemiBold,    // SemiBold — payment amount
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontSize = 24.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.SemiBold,    // SemiBold — screen titles
        letterSpacing = 0.sp,
    ),

    // ── Title ────────────────────────────────────────────────────────────────
    titleLarge = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Medium,      // Medium — merchant name
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp,
    ),

    // ── Body ─────────────────────────────────────────────────────────────────
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,      // Regular — UPI ID, transaction details
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,      // Regular — list item secondary text
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.4.sp,
    ),

    // ── Label ────────────────────────────────────────────────────────────────
    labelLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,      // Medium — button labels
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,      // Medium — status badges, category tags
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,      // Regular — timestamps, helper text
        letterSpacing = 0.5.sp,
    ),
)
