package com.offlinepay.feature.merchant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinepay.core.common.format.UpiAmountFormatter
import com.offlinepay.core.designsystem.GlassScaffold
import com.offlinepay.core.designsystem.LocalOfflinePayColors
import com.offlinepay.core.designsystem.glassTopBarColors
import com.offlinepay.core.designsystem.components.MerchantAvatar
import com.offlinepay.core.designsystem.components.StatusBadge
import com.offlinepay.core.designsystem.components.PaymentStatus
import com.offlinepay.core.domain.model.MerchantProfile
import com.offlinepay.core.domain.model.TransactionRecord
import com.offlinepay.core.domain.model.TransactionStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Merchant profile screen — displays merchant details and transaction history.
 *
 * Design reference: Section 3.10 (Merchant Profile)
 * Requirements: Req 18.1–18.7
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantProfileScreen(
    onNavigateBack: () -> Unit,
    onNavigateToReceipt: (transactionId: String) -> Unit,
    viewModel: MerchantViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    GlassScaffold(
        topBar = {
            TopAppBar(
                title = { Text("Merchant Profile") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = glassTopBarColors(),
                actions = {
                    val merchant = (uiState as? MerchantProfileUiState.Ready)?.merchant
                    if (merchant != null) {
                        IconButton(onClick = viewModel::onToggleFavourite) {
                            Icon(
                                imageVector = if (merchant.isFavourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = if (merchant.isFavourite) "Remove from favourites" else "Add to favourites",
                                tint = if (merchant.isFavourite) LocalOfflinePayColors.current.errorRed
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        when (val state = uiState) {
            is MerchantProfileUiState.Loading -> {
                Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is MerchantProfileUiState.Error -> {
                Box(Modifier.fillMaxSize().padding(paddingValues).padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is MerchantProfileUiState.Ready -> {
                MerchantProfileContent(
                    merchant = state.merchant,
                    transactions = state.transactions,
                    onTransactionTap = onNavigateToReceipt,
                    modifier = Modifier.padding(paddingValues),
                )
            }
        }
    }
}

@Composable
private fun MerchantProfileContent(
    merchant: MerchantProfile,
    transactions: List<TransactionRecord>,
    onTransactionTap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
    ) {
        // Header
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MerchantAvatar(
                    merchantName = merchant.name,
                    size = 80.dp,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = merchant.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = merchant.upiId,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${merchant.transactionCount}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Transactions",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = merchant.categoryName ?: "Merchant",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Category",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Transaction History",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (transactions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No transactions with this merchant",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(transactions, key = { it.id }) { transaction ->
                MerchantTransactionItem(
                    transaction = transaction,
                    onClick = { onTransactionTap(transaction.id) },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}

@Composable
private fun MerchantTransactionItem(
    transaction: TransactionRecord,
    onClick: () -> Unit,
) {
    val dateFormat = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dateFormat.format(Date(transaction.timestampMs)),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = transaction.paymentMethod.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = UpiAmountFormatter.formatPaiseToRupees(transaction.amountPaise),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            StatusBadge(status = transaction.status.toPaymentStatus())
        }
    }
}

private fun TransactionStatus.toPaymentStatus(): PaymentStatus = when (this) {
    TransactionStatus.SUCCESS -> PaymentStatus.SUCCESS
    TransactionStatus.FAILURE -> PaymentStatus.FAILURE
    TransactionStatus.PENDING -> PaymentStatus.PENDING
    TransactionStatus.UNKNOWN -> PaymentStatus.UNKNOWN
}
