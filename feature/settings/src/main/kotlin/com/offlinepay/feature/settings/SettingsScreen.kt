package com.offlinepay.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinepay.core.designsystem.GlassCard
import com.offlinepay.core.designsystem.GlassScaffold
import com.offlinepay.core.designsystem.glassTopBarColors
import com.offlinepay.core.designsystem.pressScale
import com.offlinepay.core.designsystem.rememberPressInteraction
import com.offlinepay.core.domain.model.AppTheme
import com.offlinepay.core.domain.model.PaymentMethodType

/**
 * Main Settings screen composable.
 *
 * Design reference: Section 10.2, Section 3.10
 * Requirements: Req 11.1–11.9
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateTo: (String) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.NavigateTo -> onNavigateTo(event.route)
                is SettingsEvent.ShowMessage -> { /* Show snackbar */ }
            }
        }
    }

    GlassScaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = glassTopBarColors(),
            )
        },
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState()),
            ) {
                SettingsSection(title = "Payment") {
                    SettingsItem(
                        icon = Icons.Filled.Route,
                        title = "Routing Priority",
                        subtitle = "Configure payment method order per operator",
                        onClick = { viewModel.onNavigateTo("settings/routing") },
                    )
                    SettingsItem(
                        icon = Icons.Filled.SimCard,
                        title = "SIM Preference",
                        subtitle = uiState.settings.preferredSimSlot?.let { "SIM ${it + 1}" } ?: "Auto",
                        onClick = { viewModel.onNavigateTo("settings/sim") },
                    )
                    SettingsItem(
                        icon = Icons.Filled.SettingsApplications,
                        title = "Payment Method Override",
                        subtitle = uiState.settings.manualPaymentMethodOverride?.name ?: "Auto (recommended)",
                        onClick = { viewModel.onNavigateTo("settings/routing") },
                    )
                }

                HorizontalDivider()

                SettingsSection(title = "Appearance") {
                    SettingsItem(
                        icon = Icons.Filled.DarkMode,
                        title = "Theme",
                        subtitle = uiState.settings.theme.name.lowercase().replaceFirstChar { it.uppercase() },
                        onClick = { viewModel.onNavigateTo("settings/theme") },
                    )
                    SettingsItem(
                        icon = Icons.Filled.Language,
                        title = "Language",
                        subtitle = if (uiState.settings.language == "hi") "हिंदी" else "English",
                        onClick = {
                            val newLang = if (uiState.settings.language == "hi") "en" else "hi"
                            viewModel.onLanguageChanged(newLang)
                        },
                    )
                }

                HorizontalDivider()

                SettingsSection(title = "Security & Privacy") {
                    SettingsItem(
                        icon = Icons.Filled.PhoneAndroid,
                        title = "Permissions",
                        subtitle = "Camera, Phone, SIM access",
                        onClick = { viewModel.onNavigateTo("settings/permissions") },
                    )
                    SettingsItem(
                        icon = Icons.Filled.Security,
                        title = "Security Status",
                        subtitle = "View integrity check status",
                        onClick = { viewModel.onNavigateTo("settings/security") },
                    )
                }

                HorizontalDivider()

                SettingsSection(title = "About") {
                    SettingsItem(
                        icon = Icons.Filled.Info,
                        title = "About OfflinePay",
                        subtitle = "Version ${uiState.appVersion}",
                        onClick = { viewModel.onNavigateTo("settings/about") },
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Settings sub-screens
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Theme selector screen: Light / Dark / System default.
 * Requirements: Req 11.2, Req 11.3
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsThemeScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    GlassScaffold(
        topBar = {
            TopAppBar(
                title = { Text("Theme") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = glassTopBarColors(),
            )
        },
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).padding(16.dp)) {
            AppTheme.entries.forEach { theme ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.onThemeChanged(theme) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = uiState.settings.theme == theme,
                        onClick = { viewModel.onThemeChanged(theme) },
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = when (theme) {
                            AppTheme.SYSTEM -> "System default"
                            AppTheme.LIGHT -> "Light"
                            AppTheme.DARK -> "Dark"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

/**
 * Permissions screen listing Camera, CALL_PHONE, READ_PHONE_STATE.
 * Requirements: Req 11.5
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPermissionsScreen(
    onNavigateBack: () -> Unit,
) {
    GlassScaffold(
        topBar = {
            TopAppBar(
                title = { Text("Permissions") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PermissionRow(name = "Camera", description = "Required for QR code scanning")
            PermissionRow(name = "Phone (CALL_PHONE)", description = "Required for USSD and 123PAY calls")
            PermissionRow(name = "Phone State (READ_PHONE_STATE)", description = "Required for SIM detection")
        }
    }
}

@Composable
private fun PermissionRow(name: String, description: String) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * About screen: version, privacy policy, licenses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAboutScreen(onNavigateBack: () -> Unit) {
    GlassScaffold(
        topBar = {
            TopAppBar(
                title = { Text("About OfflinePay") },
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
            modifier = Modifier
                .padding(paddingValues)
                .padding(24.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            Text("OfflinePay", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Version 1.0.0", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "OfflinePay enables UPI payments without internet using USSD (*99#) and 123PAY (IVR).",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Privacy Policy", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Open Source Licenses", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * Security screen showing integrity status with refresh button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSecurityScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    GlassScaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security") },
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
            modifier = Modifier
                .padding(paddingValues)
                .padding(24.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Filled.Security,
                contentDescription = "Security status",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Device Integrity", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Integrity checks verify your device is secure for payments.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = viewModel::onRefreshIntegrity,
                enabled = !uiState.isRefreshingIntegrity,
            ) {
                if (uiState.isRefreshingIntegrity) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Refresh Integrity Check")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        content()
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val interaction = rememberPressInteraction()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
