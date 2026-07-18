package com.offlinepay.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinepay.core.designsystem.GlassCard
import com.offlinepay.core.designsystem.GlassScaffold
import com.offlinepay.core.designsystem.glassTopBarColors
import com.offlinepay.core.domain.model.OperatorType
import com.offlinepay.core.domain.model.PaymentMethodType

/**
 * Settings sub-screen for configuring payment routing priority per operator.
 *
 * Displays the current routing priority for each operator (Airtel, Vi, BSNL, Jio, Other)
 * with drag-to-reorder support and manual override toggle.
 *
 * Design reference: Section 3.10 (Settings Routing)
 * Requirements: Req 4.2 (configurable priority), Req 4.10 (immediate application),
 *               Req 11.4 (routing settings), Req 11.6 (manual override)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoutingScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val config = uiState.settings.routingPriorityConfig

    GlassScaffold(
        topBar = {
            TopAppBar(
                title = { Text("Routing Priority") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = glassTopBarColors(),
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.padding(paddingValues).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Manual override section
            item {
                Text(
                    text = "Payment Method Override",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OverrideOption(
                    label = "Auto (recommended)",
                    selected = uiState.settings.manualPaymentMethodOverride == null,
                    onClick = { viewModel.onManualOverrideChanged(null) },
                )
                OverrideOption(
                    label = "Always use USSD (*99#)",
                    selected = uiState.settings.manualPaymentMethodOverride == PaymentMethodType.USSD,
                    onClick = { viewModel.onManualOverrideChanged(PaymentMethodType.USSD) },
                )
                OverrideOption(
                    label = "Always use 123PAY (IVR)",
                    selected = uiState.settings.manualPaymentMethodOverride == PaymentMethodType.PAY123,
                    onClick = { viewModel.onManualOverrideChanged(PaymentMethodType.PAY123) },
                )
            }

            // Per-operator priority
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Operator Priority Order",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "First method in the list is tried first. Drag to reorder.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val operators = listOf(
                "Airtel" to config.airtelPriority,
                "Vi" to config.viPriority,
                "BSNL" to config.bsnlPriority,
                "Jio" to config.jioPriority,
                "Other" to config.otherPriority,
            )

            items(operators) { (name, priority) ->
                OperatorPriorityCard(operatorName = name, priority = priority)
            }
        }
    }
}

@Composable
private fun OverrideOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun OperatorPriorityCard(
    operatorName: String,
    priority: List<PaymentMethodType>,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = operatorName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            priority.forEachIndexed { index, method ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .semantics { contentDescription = "$operatorName priority ${index + 1}: ${method.name}" },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.DragHandle,
                        contentDescription = "Drag to reorder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "${index + 1}. ${if (method == PaymentMethodType.USSD) "USSD (*99#)" else "123PAY (IVR)"}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

/**
 * Settings sub-screen for SIM preference selection.
 *
 * Displays available SIM slots and allows the user to select a preferred SIM
 * for payments, or use auto-detection.
 *
 * Design reference: Section 3.10 (Settings SIM)
 * Requirements: Req 11.4 (SIM preference)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSimScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    GlassScaffold(
        topBar = {
            TopAppBar(
                title = { Text("SIM Preference") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = glassTopBarColors(),
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier.padding(paddingValues).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Select the SIM card to use for offline payments.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Auto option
            SimOption(
                label = "Auto (detect best SIM)",
                selected = uiState.settings.preferredSimSlot == null,
                onClick = { viewModel.onPreferredSimChanged(null) },
            )
            // SIM 1
            SimOption(
                label = "SIM 1",
                selected = uiState.settings.preferredSimSlot == 0,
                onClick = { viewModel.onPreferredSimChanged(0) },
            )
            // SIM 2
            SimOption(
                label = "SIM 2",
                selected = uiState.settings.preferredSimSlot == 1,
                onClick = { viewModel.onPreferredSimChanged(1) },
            )
        }
    }
}

@Composable
private fun SimOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        elevated = selected,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}
