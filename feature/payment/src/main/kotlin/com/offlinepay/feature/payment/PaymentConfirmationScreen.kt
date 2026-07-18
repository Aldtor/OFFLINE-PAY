package com.offlinepay.feature.payment

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinepay.core.designsystem.OfflinePayBackground
import com.offlinepay.core.designsystem.components.AmountInput
import com.offlinepay.core.designsystem.components.MerchantAvatar
import com.offlinepay.core.designsystem.components.SimDisplayInfo
import com.offlinepay.core.designsystem.components.SimSelector
import com.offlinepay.core.domain.model.PaymentMethodType
import com.offlinepay.core.domain.model.PaymentParams
import com.offlinepay.core.domain.model.QrType
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import com.offlinepay.core.security.clipboard.ClipboardClearWorker


/**
 * Payment Confirmation screen composable.
 *
 * Collects [PaymentConfirmationUiState] from [PaymentConfirmationViewModel] and renders:
 * - Loading: centered [CircularProgressIndicator].
 * - Error: centered error message.
 * - Ready: merchant header, QR type badge, amount input, SIM selector, routing
 *          indicator, and confirm button.
 *
 * Security: [FLAG_SECURE] is added to the window while this screen is visible to
 * prevent screenshots and screen-recording capture of payment details (Req 9.23).
 *
 * Layout: two-column side-by-side layout is used on screens wider than 600 dp
 * (landscape tablets / large phones).
 *
 * Design reference: Section 10.2, 11.1, 11.5
 * Requirements: Req 7.1–7.7, Req 9.23
 *
 * @param onNavigateToUssd    Called when routing directs to USSD with payment params.
 * @param onNavigateToPay123  Called when routing directs to 123PAY with payment params.
 * @param viewModel           Injected by Hilt; override in previews/tests.
 */
@Composable
fun PaymentConfirmationScreen(
    onNavigateToUssd: (PaymentParams, Int) -> Unit,
    onNavigateToPay123: (PaymentParams, Int) -> Unit,
    viewModel: PaymentConfirmationViewModel = hiltViewModel(),
) {
    // ── FLAG_SECURE ───────────────────────────────────────────────────────────
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? android.app.Activity)?.window
        window?.addFlags(FLAG_SECURE)
        onDispose {
            window?.clearFlags(FLAG_SECURE)
        }
    }

    // ── Collect state and events ──────────────────────────────────────────────
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PaymentConfirmationEvent.NavigateToUssd ->
                    onNavigateToUssd(event.params, event.subscriptionId)

                is PaymentConfirmationEvent.NavigateToPay123 ->
                    onNavigateToPay123(event.params, event.subscriptionId)

                is PaymentConfirmationEvent.ShowError -> {
                    // No-op here — the ViewModel sets amountError in Ready state for
                    // validation errors. For routing errors, a generic snackbar would be
                    // wired here by the parent NavGraph.
                }
            }
        }
    }

    // ── Render by state ───────────────────────────────────────────────────────
    OfflinePayBackground {
        when (val state = uiState) {
            is PaymentConfirmationUiState.Loading -> {
                LoadingContent()
            }

            is PaymentConfirmationUiState.Error -> {
                ErrorContent(message = state.message)
            }

            is PaymentConfirmationUiState.Ready -> {
                ReadyContent(
                    state = state,
                    onAmountChanged = { viewModel.onAction(PaymentConfirmationAction.AmountChanged(it)) },
                    onSimSelected = { viewModel.onAction(PaymentConfirmationAction.SimSelected(it)) },
                    onConfirm = { viewModel.onAction(PaymentConfirmationAction.ConfirmPayment) },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Loading
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Error
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ErrorContent(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Ready
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ReadyContent(
    state: PaymentConfirmationUiState.Ready,
    onAmountChanged: (String) -> Unit,
    onSimSelected: (SimDisplayInfo) -> Unit,
    onConfirm: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val isWide = configuration.screenWidthDp > 600

    if (isWide) {
        TwoColumnLayout(
            state = state,
            onAmountChanged = onAmountChanged,
            onSimSelected = onSimSelected,
            onConfirm = onConfirm,
        )
    } else {
        SingleColumnLayout(
            state = state,
            onAmountChanged = onAmountChanged,
            onSimSelected = onSimSelected,
            onConfirm = onConfirm,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Single-column layout (portrait / small screens)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SingleColumnLayout(
    state: PaymentConfirmationUiState.Ready,
    onAmountChanged: (String) -> Unit,
    onSimSelected: (SimDisplayInfo) -> Unit,
    onConfirm: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MerchantHeader(state.paymentParams)
        QrTypeBadge(state.paymentParams.qrType)
        AmountInput(
            value = state.editableAmountStr,
            onValueChange = onAmountChanged,
            isEditable = state.isStaticQr,
            errorMessage = state.amountError,
            modifier = Modifier.fillMaxWidth(),
        )
        SimSelector(
            simList = state.sims,
            selectedSimSubscriptionId = state.selectedSim?.subscriptionId,
            onSimSelected = onSimSelected,
            modifier = Modifier.fillMaxWidth(),
        )
        RoutingMethodIndicator(state)
        Spacer(Modifier.height(8.dp))
        ConfirmButton(onConfirm = onConfirm)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Two-column layout (landscape / large screens > 600 dp)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TwoColumnLayout(
    state: PaymentConfirmationUiState.Ready,
    onAmountChanged: (String) -> Unit,
    onSimSelected: (SimDisplayInfo) -> Unit,
    onConfirm: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Left column — merchant info + QR badge
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MerchantHeader(state.paymentParams)
            QrTypeBadge(state.paymentParams.qrType)
        }

        // Right column — amount, SIM selector, routing, confirm
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AmountInput(
                value = state.editableAmountStr,
                onValueChange = onAmountChanged,
                isEditable = state.isStaticQr,
                errorMessage = state.amountError,
                modifier = Modifier.fillMaxWidth(),
            )
            SimSelector(
                simList = state.sims,
                selectedSimSubscriptionId = state.selectedSim?.subscriptionId,
                onSimSelected = onSimSelected,
                modifier = Modifier.fillMaxWidth(),
            )
            RoutingMethodIndicator(state)
            Spacer(Modifier.height(8.dp))
            ConfirmButton(onConfirm = onConfirm)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Merchant header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MerchantHeader(params: PaymentParams) {
    val context = LocalContext.current
    val merchantName = params.payeeName ?: params.upiId
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("UPI ID", params.upiId)
                clipboard.setPrimaryClip(clip)
                ClipboardClearWorker.schedule(context, params.upiId)
            },
    ) {
        MerchantAvatar(
            merchantName = merchantName,
            size = 56.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = merchantName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = params.upiId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// QR type badge
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun QrTypeBadge(qrType: QrType) {
    val label = when (qrType) {
        QrType.STATIC -> "Static QR"
        QrType.DYNAMIC -> "Dynamic QR"
        QrType.MERCHANT -> "Merchant QR"
        QrType.PERSONAL -> "Personal QR"
        QrType.INTENT -> "Intent QR"
        QrType.BHARAT -> "Bharat QR"
    }
    SuggestionChip(
        onClick = {},
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
            )
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Payment method indicator
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RoutingMethodIndicator(state: PaymentConfirmationUiState.Ready) {
    val methodLabel = when (state.routingDecision?.selectedMethod) {
        PaymentMethodType.USSD -> "Via USSD"
        PaymentMethodType.PAY123 -> "Via 123PAY"
        null -> "No payment method available"
    }
    Text(
        text = methodLabel,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Confirm button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ConfirmButton(onConfirm: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Button(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onConfirm()
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = "Confirm Payment")
    }
}
