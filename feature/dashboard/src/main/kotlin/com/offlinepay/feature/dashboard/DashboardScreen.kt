package com.offlinepay.feature.dashboard

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material.icons.filled.SignalCellularOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinepay.core.common.format.UpiAmountFormatter
import com.offlinepay.core.connectivity.ConnectivityState
import com.offlinepay.core.designsystem.GlassCard
import com.offlinepay.core.designsystem.GlassScaffold
import com.offlinepay.core.designsystem.LocalOfflinePayColors
import com.offlinepay.core.designsystem.glassTopBarColors
import com.offlinepay.core.designsystem.components.DismissibleSecurityBanner
import com.offlinepay.core.designsystem.components.MerchantAvatar
import com.offlinepay.core.designsystem.components.PaymentStatus
import com.offlinepay.core.designsystem.components.PersistentSecurityBanner
import com.offlinepay.core.designsystem.components.SkeletonCard
import com.offlinepay.core.designsystem.components.StatusBadge
import com.offlinepay.core.domain.model.MerchantProfile
import com.offlinepay.core.domain.model.TransactionStatus

// ─────────────────────────────────────────────────────────────────────────────
// Public entry point
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Dashboard screen composable — the app's main landing page.
 *
 * Collects [DashboardUiState] from [DashboardViewModel] and renders:
 * - Connectivity + security banners (Task 28.4)
 * - Stats cards (today / this month)
 * - Last payment summary card
 * - Offline payment status indicator (Task 28.3)
 * - Recent merchants horizontal row
 * - Favourite merchants vertical list
 * - Empty state when no activity exists
 * - Skeleton loading placeholders while data loads
 *
 * Design reference: Section 10.2 (DashboardScreen)
 * Requirements: Req 1.2, Req 9.3, Req 9.4, Req 17.1–17.10
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToScanner: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToMerchantProfile: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // One-shot navigation/side-effect event collection
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                DashboardUiEvent.NavigateToScanner -> onNavigateToScanner()
                DashboardUiEvent.NavigateToHistory -> onNavigateToHistory()
                is DashboardUiEvent.NavigateToMerchantProfile -> onNavigateToMerchantProfile(event.upiId)
                DashboardUiEvent.NavigateToSettings -> onNavigateToSettings()
            }
        }
    }

    GlassScaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "OfflinePay",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.onAction(DashboardUiAction.OpenSettings) },
                        modifier = Modifier.semantics { contentDescription = "Settings" },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = null,
                        )
                    }
                },
                colors = glassTopBarColors(),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.onAction(DashboardUiAction.ScanQr) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.semantics { contentDescription = "Scan QR code to pay" },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.QrCodeScanner,
                        contentDescription = null,
                    )
                },
                text = { Text(text = "Scan QR") },
            )
        },
    ) { paddingValues ->
        when (val state = uiState) {
            is DashboardUiState.Loading -> DashboardLoadingContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
            )

            is DashboardUiState.Ready -> DashboardReadyContent(
                state = state,
                onAction = viewModel::onAction,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            )

            is DashboardUiState.Error -> DashboardErrorContent(
                message = state.message,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Loading state — 3 skeleton cards
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DashboardLoadingContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        repeat(3) {
            SkeletonCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Error state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DashboardErrorContent(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Ready state — full dashboard content
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DashboardReadyContent(
    state: DashboardUiState.Ready,
    onAction: (DashboardUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasActivity = state.lastPayment != null ||
        state.recentMerchants.isNotEmpty() ||
        state.todayCount > 0 ||
        state.monthCount > 0

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // ── Task 28.4: Banners (at very top) ───────────────────────────────
        DashboardBanners(
            connectivityState = state.connectivityState,
            securityTier = state.securityTier,
            connectivityBannerDismissed = state.connectivityBannerDismissed,
            securityBannerDismissed = state.securityBannerDismissed,
            onDismissConnectivity = { onAction(DashboardUiAction.DismissConnectivityBanner) },
            onDismissSecurity = { onAction(DashboardUiAction.DismissSecurityBanner) },
        )

        // ── Stats row: today + this month ──────────────────────────────────
        StatsRow(
            todayCount = state.todayCount,
            todayTotalPaise = state.todayTotalPaise,
            monthCount = state.monthCount,
            monthTotalPaise = state.monthTotalPaise,
        )

        // ── Last payment ───────────────────────────────────────────────────
        if (state.lastPayment != null) {
            LastPaymentCard(summary = state.lastPayment)
        } else {
            Text(
                text = "No recent payments",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ── Task 28.3: Offline payment status indicator ────────────────────
        OfflineStatusCard(simStatus = state.simStatus)

        // ── Recent Merchants ───────────────────────────────────────────────
        SectionHeader(
            title = "Recent",
            actionLabel = "See all →",
            onAction = { onAction(DashboardUiAction.ViewHistory) },
        )
        if (state.recentMerchants.isEmpty()) {
            Text(
                text = "No recent merchants",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            RecentMerchantsRow(
                merchants = state.recentMerchants,
                onMerchantClick = { upiId -> onAction(DashboardUiAction.OpenMerchantProfile(upiId)) },
            )
        }

        // ── Favourite Merchants ────────────────────────────────────────────
        SectionHeader(title = "Favourites")
        if (state.favouriteMerchants.isEmpty()) {
            Text(
                text = "No favourites yet. Tap ♡ on a merchant to save.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FavouriteMerchantsList(
                merchants = state.favouriteMerchants,
                onMerchantClick = { upiId -> onAction(DashboardUiAction.OpenMerchantProfile(upiId)) },
            )
        }

        // ── Empty state ────────────────────────────────────────────────────
        if (!hasActivity) {
            DashboardEmptyState(onScanClick = { onAction(DashboardUiAction.ScanQr) })
        }

        // FAB clearance
        Spacer(modifier = Modifier.height(88.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Task 28.4 — Banners
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Dashboard informational banners rendered at the top of the ready state.
 *
 * 1. Connectivity banner — amber dismissible, shown when [ConnectivityState.Online]
 *    and not yet dismissed.
 * 2. Security Tier 2 banner — amber dismissible, shown when securityTier == 2.
 * 3. Security Tier 3+ banner — red persistent (non-dismissible).
 *
 * Design reference: Section 10.2, Section 8.2
 * Requirements: Req 1.2, Req 9.3, Req 9.4
 */
@Composable
private fun DashboardBanners(
    connectivityState: ConnectivityState,
    securityTier: Int,
    connectivityBannerDismissed: Boolean,
    securityBannerDismissed: Boolean,
    onDismissConnectivity: () -> Unit,
    onDismissSecurity: () -> Unit,
) {
    // 1. Connectivity informational banner (amber/dismissible)
    //    Shown when internet is detected — OfflinePay continues using offline methods.
    DismissibleSecurityBanner(
        visible = connectivityState is ConnectivityState.Online && !connectivityBannerDismissed,
        message = "Internet detected. OfflinePay continues using offline payment methods.",
        onDismiss = onDismissConnectivity,
    )

    // 2. Security Tier 2 banner — stale verdict, dismissible amber
    DismissibleSecurityBanner(
        visible = securityTier == 2 && !securityBannerDismissed,
        message = "Security check is due soon. Integrity will be refreshed automatically.",
        onDismiss = onDismissSecurity,
    )

    // 3. Security Tier 3+ banner — very stale / no verdict, persistent red (no dismiss)
    PersistentSecurityBanner(
        visible = securityTier >= 3,
        message = "Security check overdue. Payment features may be limited.",
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Stats row — today + this month side by side
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatsRow(
    todayCount: Int,
    todayTotalPaise: Long,
    monthCount: Int,
    monthTotalPaise: Long,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatCard(
            title = "Today",
            count = todayCount,
            totalPaise = todayTotalPaise,
            modifier = Modifier.weight(1f),
        )
        StatCard(
            title = "This Month",
            count = monthCount,
            totalPaise = monthTotalPaise,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCard(
    title: String,
    count: Int,
    totalPaise: Long,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$count payments",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = UpiAmountFormatter.formatPaiseToRupees(totalPaise),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Last payment card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LastPaymentCard(summary: LastPaymentSummary) {
    GlassCard(modifier = Modifier.fillMaxWidth(), elevated = true) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MerchantAvatar(
                merchantName = summary.merchantName,
                size = 40.dp,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = summary.merchantName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = UpiAmountFormatter.formatPaiseToRupees(summary.amountPaise),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = relativeTime(summary.timestampMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusBadge(status = summary.status.toPaymentStatus())
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Task 28.3 — Offline payment status indicator
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Card showing the current offline payment method availability derived from
 * SIM operator and voice service state.
 *
 * - [SimPaymentStatus.UssdAvailable] → green chip, "USSD · {name}"
 * - [SimPaymentStatus.Pay123Available] → blue chip, "123PAY · {name}"
 * - [SimPaymentStatus.NoService] → grey chip, "No voice service available"
 *
 * Subtitle informs the user the value is auto-detected and overridable.
 *
 * Design reference: Section 10.2 (OfflineStatusCard)
 * Requirements: Req 17.8, Req 17.9
 */
@Composable
private fun OfflineStatusCard(simStatus: SimPaymentStatus) {
    val colors = LocalOfflinePayColors.current

    val (chipColor, statusText) = when (simStatus) {
        is SimPaymentStatus.UssdAvailable -> colors.successGreen to "USSD · ${simStatus.operatorName}"
        is SimPaymentStatus.Pay123Available -> colors.infoBlue to "123PAY · ${simStatus.operatorName}"
        SimPaymentStatus.NoService -> colors.unknownGrey to "No voice service available"
    }

    val serviceIcon = when (simStatus) {
        is SimPaymentStatus.UssdAvailable,
        is SimPaymentStatus.Pay123Available -> Icons.Filled.SignalCellular4Bar
        SimPaymentStatus.NoService -> Icons.Filled.SignalCellularOff
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = serviceIcon,
                contentDescription = "Offline payment status",
                tint = chipColor,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Offline Payment",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = chipColor,
                )
                Text(
                    text = "Auto-detected · Go to Settings to override",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Recent merchants horizontal row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RecentMerchantsRow(
    merchants: List<MerchantProfile>,
    onMerchantClick: (String) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 0.dp),
    ) {
        items(merchants, key = { it.upiId }) { merchant ->
            Column(
                modifier = Modifier
                    .clickable { onMerchantClick(merchant.upiId) }
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MerchantAvatar(
                    merchantName = merchant.name,
                    size = 48.dp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = merchant.name,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(64.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Favourite merchants vertical list
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FavouriteMerchantsList(
    merchants: List<MerchantProfile>,
    onMerchantClick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        merchants.forEach { merchant ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onMerchantClick(merchant.upiId) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MerchantAvatar(
                    merchantName = merchant.name,
                    size = 40.dp,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = merchant.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DashboardEmptyState(onScanClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "No payments yet.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onScanClick) {
            Text(text = "Scan QR Code")
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section header with optional action
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Converts a UTC epoch millisecond timestamp into a human-readable relative
 * time string suitable for UI display.
 *
 * Rules:
 * - < 1 min  → "Just now"
 * - < 60 min → "X min ago"
 * - < 24 h   → "X hours ago"
 * - < 48 h   → "Yesterday"
 * - else     → "X days ago"
 *
 * Requirements: Req 17.3 (last payment relative timestamp)
 */
internal fun relativeTime(timestampMs: Long): String {
    val nowMs = System.currentTimeMillis()
    val diffMs = nowMs - timestampMs
    val diffMinutes = diffMs / (60 * 1_000L)
    val diffHours = diffMs / (60 * 60 * 1_000L)
    val diffDays = diffMs / (24 * 60 * 60 * 1_000L)

    return when {
        diffMinutes < 1 -> "Just now"
        diffMinutes < 60 -> "$diffMinutes min ago"
        diffHours < 24 -> "$diffHours hours ago"
        diffDays < 2 -> "Yesterday"
        else -> "$diffDays days ago"
    }
}

private fun TransactionStatus.toPaymentStatus(): PaymentStatus = when (this) {
    TransactionStatus.SUCCESS -> PaymentStatus.SUCCESS
    TransactionStatus.FAILURE -> PaymentStatus.FAILURE
    TransactionStatus.PENDING -> PaymentStatus.PENDING
    TransactionStatus.UNKNOWN -> PaymentStatus.UNKNOWN
}
