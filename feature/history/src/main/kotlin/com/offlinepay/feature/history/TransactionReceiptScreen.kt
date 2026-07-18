package com.offlinepay.feature.history

import android.view.WindowManager.LayoutParams.FLAG_SECURE
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinepay.core.common.format.UpiAmountFormatter
import com.offlinepay.core.designsystem.GlassCard
import com.offlinepay.core.designsystem.GlassScaffold
import com.offlinepay.core.designsystem.glassTopBarColors
import com.offlinepay.core.designsystem.components.MerchantAvatar
import com.offlinepay.core.designsystem.components.StatusBadge
import com.offlinepay.core.designsystem.components.PaymentStatus
import com.offlinepay.core.domain.model.TransactionRecord
import com.offlinepay.core.domain.model.TransactionStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Transaction receipt screen — displays all stored fields of a transaction.
 *
 * Security: FLAG_SECURE is active while this screen is visible (Req 9.23).
 *
 * Design reference: Section 10.1 (transaction_receipt route)
 * Requirements: Req 8.14, Req 8.18, Req 9.23
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionReceiptScreen(
    onNavigateBack: () -> Unit,
    viewModel: TransactionReceiptViewModel = hiltViewModel(),
) {
    // ── FLAG_SECURE ──
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? android.app.Activity)?.window
        window?.addFlags(FLAG_SECURE)
        onDispose { window?.clearFlags(FLAG_SECURE) }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is TransactionReceiptEvent.NavigateBack -> onNavigateBack()
                is TransactionReceiptEvent.ShareReceipt -> { /* Launch share intent */ }
                is TransactionReceiptEvent.ShowError -> { /* Show snackbar */ }
            }
        }
    }

    GlassScaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transaction Receipt") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = glassTopBarColors(),
                actions = {
                    IconButton(onClick = viewModel::onShareTapped) {
                        Icon(Icons.Filled.Share, contentDescription = "Share receipt")
                    }
                    IconButton(onClick = viewModel::onDeleteTapped) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete transaction",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        when (val state = uiState) {
            is TransactionReceiptUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            is TransactionReceiptUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues).padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            is TransactionReceiptUiState.Ready -> {
                ReceiptContent(
                    transaction = state.transaction,
                    modifier = Modifier.padding(paddingValues),
                )

                // Delete confirmation dialog
                if (state.showDeleteConfirmation) {
                    AlertDialog(
                        onDismissRequest = viewModel::onDeleteCancelled,
                        title = { Text("Delete Transaction") },
                        text = { Text("Are you sure you want to delete this transaction record? This action cannot be undone.") },
                        confirmButton = {
                            TextButton(onClick = viewModel::onDeleteConfirmed) {
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = viewModel::onDeleteCancelled) {
                                Text("Cancel")
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReceiptContent(
    transaction: TransactionRecord,
    modifier: Modifier = Modifier,
) {
    val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault())

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Merchant info + Status
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            elevated = true,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MerchantAvatar(
                    merchantName = transaction.merchantName ?: transaction.payeeName ?: "M",
                    size = 64.dp,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = transaction.merchantName ?: transaction.payeeName ?: transaction.payeeUpiId,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = transaction.payeeUpiId,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = UpiAmountFormatter.formatPaiseToRupees(transaction.amountPaise),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                StatusBadge(status = transaction.status.toPaymentStatus())
            }
        }

        // Transaction details
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                ReceiptRow(label = "Transaction ID", value = transaction.id)
                ReceiptDivider()
                ReceiptRow(label = "Date & Time", value = dateFormat.format(Date(transaction.timestampMs)))
                ReceiptDivider()
                ReceiptRow(label = "Payment Method", value = when (transaction.paymentMethod) {
                    com.offlinepay.core.domain.model.PaymentMethodType.USSD -> "USSD (*99#)"
                    com.offlinepay.core.domain.model.PaymentMethodType.PAY123 -> "123PAY (IVR)"
                })
                ReceiptDivider()
                ReceiptRow(label = "Operator", value = transaction.operatorUsed?.name ?: "Unknown")
                ReceiptDivider()
                ReceiptRow(label = "SIM Slot", value = "SIM ${transaction.simSlotUsed + 1}")
                transaction.transactionReference?.let {
                    ReceiptDivider()
                    ReceiptRow(label = "Reference", value = it)
                }
                transaction.merchantCategoryCode?.let {
                    ReceiptDivider()
                    ReceiptRow(label = "Category Code", value = it)
                }
            }
        }
    }
}

@Composable
private fun ReceiptRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1.5f).padding(start = 8.dp),
        )
    }
}

@Composable
private fun ReceiptDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 10.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

private fun TransactionStatus.toPaymentStatus(): PaymentStatus = when (this) {
    TransactionStatus.SUCCESS -> PaymentStatus.SUCCESS
    TransactionStatus.FAILURE -> PaymentStatus.FAILURE
    TransactionStatus.PENDING -> PaymentStatus.PENDING
    TransactionStatus.UNKNOWN -> PaymentStatus.UNKNOWN
}
