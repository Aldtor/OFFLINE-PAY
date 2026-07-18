package com.offlinepay.feature.merchant

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.offlinepay.core.designsystem.LocalOfflinePayColors
import com.offlinepay.core.designsystem.components.MerchantAvatar
import com.offlinepay.core.domain.model.MerchantProfile

/**
 * Merchant card composable per Req 18.
 *
 * Displays:
 * - Merchant name (from `pn` field)
 * - Category (from `mc` or "Merchant" default)
 * - Generated avatar (initials + brand color)
 * - Placeholder "Verified" badge (visually distinct from active)
 * - Favourite toggle (persisted)
 * - Recent transaction count (last 30 days)
 *
 * Design reference: Section 3.10 (Merchant Card)
 * Requirements: Req 18.1–18.7
 */
@Composable
fun MerchantCard(
    merchant: MerchantProfile,
    onTap: () -> Unit,
    onFavouriteTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onTap),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar
            MerchantAvatar(
                merchantName = merchant.name,
                size = 48.dp,
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Name, category, transaction count
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = merchant.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    // Placeholder verified badge — visually distinct (grey, not checkmarked)
                    Icon(
                        imageVector = Icons.Filled.Verified,
                        contentDescription = "Verification pending",
                        tint = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier
                            .height(16.dp)
                            .width(16.dp),
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = merchant.categoryName ?: "Merchant",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${merchant.transactionCount} transactions",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Favourite toggle
            IconButton(
                onClick = onFavouriteTap,
                modifier = Modifier.semantics {
                    contentDescription = if (merchant.isFavourite)
                        "Remove from favourites"
                    else
                        "Add to favourites"
                },
            ) {
                Icon(
                    imageVector = if (merchant.isFavourite)
                        Icons.Filled.Favorite
                    else
                        Icons.Filled.FavoriteBorder,
                    contentDescription = null,
                    tint = if (merchant.isFavourite)
                        LocalOfflinePayColors.current.errorRed
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
