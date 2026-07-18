package com.offlinepay.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.offlinepay.core.designsystem.LocalOfflinePayColors
import com.offlinepay.core.designsystem.OfflinePaySpacing

// ---------------------------------------------------------------------------
// SimDisplayInfo — lightweight model, defined here to avoid circular
//                  dependency on :core:domain (Design ref: Section 3.2)
// ---------------------------------------------------------------------------

/**
 * Display-layer model for a single SIM slot.
 *
 * Deliberately kept in `:core:designsystem` rather than `:core:domain` so
 * that the design system has zero dependency on the domain module.
 *
 * Feature modules map `SimInfo` (from `:core:telephony`) to this type
 * inside their ViewModels before passing it to [SimSelector].
 *
 * @property slotIndex               Physical SIM slot index (0-based).
 * @property operatorName            Human-readable operator name (e.g. "Airtel").
 * @property isVoiceServiceAvailable Whether the SIM currently has voice service.
 * @property subscriptionId          Android subscription ID for this SIM.
 */
data class SimDisplayInfo(
    val slotIndex: Int,
    val operatorName: String,
    val isVoiceServiceAvailable: Boolean,
    val subscriptionId: Int,
)

// ---------------------------------------------------------------------------
// SimSelector
// ---------------------------------------------------------------------------

/**
 * SIM-selection UI component for dual-SIM devices.
 *
 * **Single-SIM (or empty list)**: renders nothing — a zero-size [Box] so
 * layout siblings don't shift. Callers can omit the composable entirely but
 * having it unconditionally placed keeps call-site layouts clean.
 *
 * **Dual-SIM (2 or more entries)**: renders each SIM as a selectable
 * [OutlinedCard] showing:
 * - "SIM {slotIndex + 1}" label
 * - Operator name
 * - Voice-service status icon:
 *     - ✔ `CheckCircle` (successGreen) when voice service is available
 *     - ⚠ `Warning` (warningAmber) when voice service is unavailable
 *
 * The currently selected SIM card has a highlighted 2dp border using
 * [com.offlinepay.core.designsystem.OfflinePayColors.brandPrimary].
 * Unselected cards use the Material 3 outline colour.
 *
 * ### Accessibility
 * Each card carries a `contentDescription` read by TalkBack:
 * `"SIM {slot+1}, {operatorName}, voice service {available|unavailable},
 *  {selected|not selected}"`.
 *
 * Design reference: Section 3.2, 10.3, 10.4
 * Requirements: Req 14.5, Req 14.8, Req 15.1, Req 15.6, Req 15.7
 *
 * ### Usage
 * ```kotlin
 * SimSelector(
 *     simList = uiState.simList,
 *     selectedSimSubscriptionId = uiState.selectedSubscriptionId,
 *     onSimSelected = viewModel::onSimSelected,
 *     modifier = Modifier.fillMaxWidth(),
 * )
 * ```
 *
 * @param simList                   Ordered list of available SIM slots.
 * @param selectedSimSubscriptionId The [SimDisplayInfo.subscriptionId] of the
 *                                  currently selected SIM, or `null` when none
 *                                  is selected yet.
 * @param onSimSelected             Callback invoked when the user taps a SIM card.
 * @param modifier                  Modifier applied to the row container.
 */
@Composable
fun SimSelector(
    simList: List<SimDisplayInfo>,
    selectedSimSubscriptionId: Int?,
    onSimSelected: (SimDisplayInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Single-SIM or no SIM — render nothing (zero-size placeholder).
    if (simList.size <= 1) {
        Box(modifier = modifier)
        return
    }

    val colors = LocalOfflinePayColors.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(OfflinePaySpacing.S),
    ) {
        simList.forEach { sim ->
            val isSelected = sim.subscriptionId == selectedSimSubscriptionId

            val borderColor = if (isSelected) colors.brandPrimary
            else MaterialTheme.colorScheme.outline

            val borderWidth = if (isSelected) 2.dp else 1.dp

            val voiceStatusLabel = if (sim.isVoiceServiceAvailable) "voice service available"
            else "voice service unavailable"
            val selectionLabel = if (isSelected) "selected" else "not selected"
            val cardDescription =
                "SIM ${sim.slotIndex + 1}, ${sim.operatorName}, $voiceStatusLabel, $selectionLabel"

            OutlinedCard(
                onClick = { onSimSelected(sim) },
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = cardDescription },
                border = BorderStroke(width = borderWidth, color = borderColor),
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = OfflinePaySpacing.M,
                        vertical = OfflinePaySpacing.S,
                    ),
                    verticalArrangement = Arrangement.spacedBy(OfflinePaySpacing.XS),
                ) {
                    // "SIM 1" / "SIM 2" label
                    Text(
                        text = "SIM ${sim.slotIndex + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) colors.brandPrimary
                        else MaterialTheme.colorScheme.onSurface,
                    )

                    // Operator name
                    Text(
                        text = sim.operatorName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )

                    // Voice service status icon + label
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (sim.isVoiceServiceAvailable) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null, // described by card-level CD
                                tint = colors.successGreen,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Available",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.successGreen,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null, // described by card-level CD
                                tint = colors.warningAmber,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "No service",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.warningAmber,
                            )
                        }
                    }
                }
            }
        }
    }
}
