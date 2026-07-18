package com.offlinepay.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Brand colour palette used for merchant avatars.
 *
 * Six distinct, accessible colours for deterministic assignment based on the
 * merchant name hash. Chosen to be visually distinct at all theme modes.
 * Design reference: Section 3.2 — MerchantAvatar brand color palette.
 */
private val MerchantAvatarPalette: List<Color> = listOf(
    Color(0xFF3D2DB5), // Brand Indigo
    Color(0xFF0F766E), // Teal
    Color(0xFF9333EA), // Purple
    Color(0xFFEA580C), // Orange
    Color(0xFF0369A1), // Blue
    Color(0xFF16A34A), // Green
)

/**
 * Extracts up to 2 initials from a merchant display name.
 *
 * Rules:
 * - If the name has 2+ words, takes the first letter of the first two words.
 * - If the name has 1 word, takes the first 1–2 characters.
 * - Empty name → "?" fallback.
 *
 * @param name Merchant display name (e.g. "Quick Mart", "Ravi Kumar").
 * @return Upper-cased initials string of length 1–2.
 */
internal fun extractInitials(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return "?"
    val words = trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        words.size >= 2 -> "${words[0].first()}${words[1].first()}".uppercase()
        words[0].length >= 2 -> words[0].take(2).uppercase()
        else -> words[0].first().toString().uppercase()
    }
}

/**
 * Deterministically picks a brand color for a merchant from [MerchantAvatarPalette].
 *
 * Uses the absolute value of the name's `hashCode()` modulo the palette size so the
 * same merchant always resolves to the same colour, regardless of ordering.
 *
 * @param name Merchant display name.
 * @return One of the six palette [Color] values.
 */
internal fun avatarColorForName(name: String): Color {
    val index = Math.abs(name.hashCode()) % MerchantAvatarPalette.size
    return MerchantAvatarPalette[index]
}

/**
 * Circular merchant avatar that shows generated initials on a deterministic brand colour.
 *
 * The component is a single accessibility node whose `contentDescription` is the full
 * merchant name, so screen readers announce the merchant rather than cryptic initials.
 *
 * Design reference: Section 3.2 — MerchantAvatar
 * Requirements: Req 14.5 (merchant avatar), Req 15.1 (accessibility contentDescription)
 *
 * ### Usage
 * ```kotlin
 * MerchantAvatar(
 *     merchantName = "Quick Mart",
 *     modifier = Modifier,
 *     size = 48.dp,
 * )
 * ```
 *
 * @param merchantName Display name of the merchant. Used for initials extraction,
 *                     colour selection, and the `contentDescription`.
 * @param modifier     Modifier applied to the outer [Box].
 * @param size         Diameter of the circle. Defaults to 48dp (design spec default).
 */
@Composable
fun MerchantAvatar(
    merchantName: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val initials = remember(merchantName) { extractInitials(merchantName) }
    val backgroundColor = remember(merchantName) { avatarColorForName(merchantName) }

    // Font size scales with the avatar size: ~35% of diameter
    val fontSize = (size.value * 0.35f).sp

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor)
            .semantics { contentDescription = merchantName },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            color = Color.White,
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}
