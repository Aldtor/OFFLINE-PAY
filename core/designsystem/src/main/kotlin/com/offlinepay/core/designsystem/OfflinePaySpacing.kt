package com.offlinepay.core.designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * OfflinePay spacing constants — 4dp grid system.
 *
 * All spacing values are multiples of 4dp for pixel-perfect alignment.
 * Use these tokens instead of hardcoded dp values in Composables.
 *
 * Design reference: Section 10.3 — Spacing System (4dp grid)
 *
 *   XXS =  4dp  — icon padding, tight gaps
 *   XS  =  8dp  — list item internal spacing
 *   S   = 12dp  — component internal padding
 *   M   = 16dp  — card padding, standard gap
 *   L   = 24dp  — section spacing
 *   XL  = 32dp  — screen-level margins
 *   XXL = 48dp  — large visual separations
 */
object OfflinePaySpacing {
    /** 4dp — icon padding, tight gaps */
    val XXS: Dp = 4.dp

    /** 8dp — list item internal spacing */
    val XS: Dp = 8.dp

    /** 12dp — component internal padding */
    val S: Dp = 12.dp

    /** 16dp — card padding, standard gap */
    val M: Dp = 16.dp

    /** 24dp — section spacing */
    val L: Dp = 24.dp

    /** 32dp — screen-level margins */
    val XL: Dp = 32.dp

    /** 48dp — large visual separations */
    val XXL: Dp = 48.dp
}
