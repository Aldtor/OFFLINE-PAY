package com.offlinepay.feature.history

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.offlinepay.core.common.format.UpiAmountFormatter
import com.offlinepay.core.designsystem.GlassScaffold
import com.offlinepay.core.designsystem.glassTopBarColors
import com.offlinepay.core.designsystem.components.PaymentStatus
import com.offlinepay.core.designsystem.components.SkeletonCard
import com.offlinepay.core.designsystem.components.StatusBadge
import com.offlinepay.core.domain.model.PaymentMethodType
import com.offlinepay.core.domain.model.TransactionFilters
import com.offlinepay.core.domain.model.TransactionRecord
import com.offlinepay.core.domain.model.TransactionStatus
import java.util.concurrent.TimeUnit

/**
 * History screen composable — transaction history with search, filters, paging and export.
 *
 * Uses [collectAsLazyPagingItems] so the transaction list is loaded on-demand with
 * Paging 3 semantics (skeleton loading, error/retry, empty state).
 *
 * Design reference: Section 9.3, 9.5, 10.2 (HistoryScreen)
 * Requirements: Req 8.4, Req 8.5, Req 8.8–8.11
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    onNavigateToReceipt: (transactionId: String) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pagingItems: LazyPagingItems<TransactionRecord> =
        viewModel.pagingData.collectAsLazyPagingItems()

    // Track whether the export button is in a loading state
    var isExporting by remember { mutableStateOf(false) }

    // Collect one-shot events
    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is HistoryUiEvent.NavigateToReceipt -> {
                    onNavigateToReceipt(event.transactionId)
                }
                is HistoryUiEvent.ShareExport -> {
                    isExporting = false
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_STREAM, event.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(
                        Intent.createChooser(shareIntent, "Export transactions").apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        },
                    )
                }
                is HistoryUiEvent.ShowError -> {
                    isExporting = false
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    GlassScaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transaction History") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = glassTopBarColors(),
                actions = {
                    // Export as CSV icon — per Req 8.17, Req 8.18
                    if (isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(12.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(
                            onClick = {
                                isExporting = true
                                viewModel.onAction(HistoryUiAction.ExportAsCsv)
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FileDownload,
                                contentDescription = "Export as CSV",
                            )
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // ── Search bar (OutlinedTextField) ─────────────────────────────
            // Requirements: Req 8.6 — search by payee name, merchant name, UPI ID
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { query ->
                    viewModel.onAction(HistoryUiAction.SearchQueryChanged(query))
                },
                placeholder = { Text("Search by name, UPI ID…") },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                viewModel.onAction(HistoryUiAction.SearchQueryChanged(""))
                            },
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
            )

            // ── Filter chip row (LazyRow) ──────────────────────────────────
            // Chips: status (All/Success/Failed/Pending/Unknown), method (USSD/123PAY)
            // Selected chip uses filled style (FilterChip handles this automatically).
            // "Clear" chip shown when filters are active — per task spec.
            // Requirements: Req 8.7 (status filter), Req 8.11 (method filter)
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // "All" chip — clears status filter
                item {
                    FilterChip(
                        selected = uiState.activeFilters.status == null &&
                                uiState.activeFilters.paymentMethod == null,
                        onClick = {
                            viewModel.onAction(
                                HistoryUiAction.FiltersChanged(
                                    uiState.activeFilters.copy(
                                        status = null,
                                        paymentMethod = null,
                                    ),
                                ),
                            )
                        },
                        label = { Text("All") },
                    )
                }

                // Status filter chips: Success / Failed / Pending / Unknown
                items(TransactionStatus.entries.size) { index ->
                    val status = TransactionStatus.entries[index]
                    val isSelected = uiState.activeFilters.status == status
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            viewModel.onAction(
                                HistoryUiAction.FiltersChanged(
                                    uiState.activeFilters.copy(
                                        status = if (isSelected) null else status,
                                    ),
                                ),
                            )
                        },
                        label = { Text(status.toDisplayLabel()) },
                    )
                }

                // Payment method filter chips: USSD / 123PAY
                // Requirements: Req 8.11
                items(PaymentMethodType.entries.size) { index ->
                    val method = PaymentMethodType.entries[index]
                    val isSelected = uiState.activeFilters.paymentMethod == method
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            viewModel.onAction(
                                HistoryUiAction.FiltersChanged(
                                    uiState.activeFilters.copy(
                                        paymentMethod = if (isSelected) null else method,
                                    ),
                                ),
                            )
                        },
                        label = { Text(method.toDisplayLabel()) },
                    )
                }

                // "Clear" chip — shown only when any filter is active
                if (uiState.hasActiveFilters) {
                    item {
                        FilterChip(
                            selected = false,
                            onClick = { viewModel.onAction(HistoryUiAction.ClearFilters) },
                            label = { Text("Clear") },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Clear all filters",
                                )
                            },
                        )
                    }
                }
            }

            HorizontalDivider()

            // ── Transient error banner ──────────────────────────────────────
            uiState.error?.let { errorMsg ->
                Text(
                    text = errorMsg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            // ── Content area ────────────────────────────────────────────────
            when {
                // Initial skeleton loading — 5x SkeletonCard shimmer placeholders
                // Requirements: Req 8.4 (display transactions), loadState.refresh == Loading
                pagingItems.loadState.refresh is LoadState.Loading &&
                        pagingItems.itemCount == 0 -> {
                    SkeletonLoadingContent()
                }

                // Refresh error (database failure) — error message + "Retry" button
                // Requirements: Req 8.5 — error state with retry action
                pagingItems.loadState.refresh is LoadState.Error -> {
                    val error =
                        (pagingItems.loadState.refresh as LoadState.Error).error.localizedMessage
                    ErrorContent(
                        message = error ?: "Failed to load transactions.",
                        onRetry = { pagingItems.retry() },
                    )
                }

                // Empty state — distinguishes "no transactions" vs "no matching results"
                // Requirements: Req 8.4, Req 8.5 (implicit — no silent empty list)
                pagingItems.loadState.refresh is LoadState.NotLoading &&
                        pagingItems.itemCount == 0 -> {
                    if (uiState.hasActiveFilters) {
                        // "No matching transactions." + "Clear filters" button
                        NoResultsEmptyState(
                            onClearFilters = {
                                viewModel.onAction(HistoryUiAction.ClearFilters)
                            },
                        )
                    } else {
                        // "No payments yet." empty state
                        NoPaymentsEmptyState()
                    }
                }

                // Data available — reverse-chronological order (enforced by DAO ORDER BY timestamp DESC)
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .animateContentSize(),
                    ) {
                        items(
                            count = pagingItems.itemCount,
                            key = pagingItems.itemKey { it.id },
                        ) { index ->
                            val transaction = pagingItems[index]
                            if (transaction != null) {
                                TransactionListItem(
                                    transaction = transaction,
                                    onClick = {
                                        viewModel.onAction(
                                            HistoryUiAction.OpenReceipt(transaction.id),
                                        )
                                    },
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                        }

                        // Append loading spinner
                        if (pagingItems.loadState.append is LoadState.Loading) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(strokeWidth = 2.dp)
                                }
                            }
                        }

                        // Append error with retry
                        if (pagingItems.loadState.append is LoadState.Error) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Button(onClick = { pagingItems.retry() }) {
                                        Text("Retry")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Transaction List Item
// StatusBadge + merchant name + amount + relative timestamp
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TransactionListItem(
    transaction: TransactionRecord,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.merchantName
                    ?: transaction.payeeName
                    ?: transaction.payeeUpiId,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Relative timestamp — "2 minutes ago", "just now", etc.
                Text(
                    text = relativeTimestamp(transaction.timestampMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = transaction.paymentMethod.toDisplayLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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

// ─────────────────────────────────────────────────────────────────────────────
// Skeleton loading — 5x SkeletonCard shimmer placeholders on initial load
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SkeletonLoadingContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(5) {
            SkeletonCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Error content — per Req 8.5: error state with retry action
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty states
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Empty state when no filters are active and there are no transactions at all.
 * Shown as: "No payments yet."
 */
@Composable
private fun NoPaymentsEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = "No payments yet.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Scan a QR code to make your first payment.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Empty state when filters are active but no transactions match.
 * Shown as: "No matching transactions." + "Clear filters" button.
 */
@Composable
private fun NoResultsEmptyState(onClearFilters: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = "No matching transactions.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Try adjusting your filters or search query.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onClearFilters) {
                Text("Clear filters")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Relative timestamp helper
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Returns a human-readable relative timestamp string for the given epoch milliseconds.
 *
 * Examples: "just now", "5 minutes ago", "2 hours ago", "3 days ago", "12 Feb 2024"
 *
 * Design reference: Section 10.2 (TransactionListItem — relative timestamp)
 */
private fun relativeTimestamp(timestampMs: Long): String {
    val now = System.currentTimeMillis()
    val diffMs = now - timestampMs
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMs)
    val hours = TimeUnit.MILLISECONDS.toHours(diffMs)
    val days = TimeUnit.MILLISECONDS.toDays(diffMs)

    return when {
        diffMs < TimeUnit.MINUTES.toMillis(1) -> "just now"
        minutes < 60 -> "$minutes min ago"
        hours < 24 -> "$hours hr ago"
        days == 1L -> "yesterday"
        days < 7 -> "$days days ago"
        else -> {
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = timestampMs
            val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
            val month = cal.getDisplayName(
                java.util.Calendar.MONTH,
                java.util.Calendar.SHORT,
                java.util.Locale.getDefault(),
            ) ?: ""
            val year = cal.get(java.util.Calendar.YEAR)
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            if (year == currentYear) "$day $month" else "$day $month $year"
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Display label helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun TransactionStatus.toDisplayLabel(): String = when (this) {
    TransactionStatus.SUCCESS -> "Success"
    TransactionStatus.FAILURE -> "Failed"
    TransactionStatus.PENDING -> "Pending"
    TransactionStatus.UNKNOWN -> "Unknown"
}

private fun PaymentMethodType.toDisplayLabel(): String = when (this) {
    PaymentMethodType.USSD -> "USSD"
    PaymentMethodType.PAY123 -> "123PAY"
}

// ─────────────────────────────────────────────────────────────────────────────
// Status mapping
// ─────────────────────────────────────────────────────────────────────────────

private fun TransactionStatus.toPaymentStatus(): PaymentStatus = when (this) {
    TransactionStatus.SUCCESS -> PaymentStatus.SUCCESS
    TransactionStatus.FAILURE -> PaymentStatus.FAILURE
    TransactionStatus.PENDING -> PaymentStatus.PENDING
    TransactionStatus.UNKNOWN -> PaymentStatus.UNKNOWN
}
