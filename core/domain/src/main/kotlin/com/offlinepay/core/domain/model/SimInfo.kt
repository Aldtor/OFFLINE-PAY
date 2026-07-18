package com.offlinepay.core.domain.model

import kotlinx.serialization.Serializable

/**
 * Information about a single SIM card subscription detected on the device.
 *
 * Produced by `SIM_Detector` (`:core:telephony`) and consumed by the
 * [RoutingEngine] to determine the appropriate offline payment method.
 *
 * Design reference: Section 4.4 (SimInfo data model), Section 3.9 (`:core:telephony`)
 * Requirements: Req 3.1 (SubscriptionManager detection), Req 3.2 (operator identification)
 *
 * @param subscriptionId Android telephony subscription ID.
 * @param operatorName Raw operator name string from the SIM card.
 * @param operatorType Resolved [OperatorType] enum value.
 * @param slotIndex SIM slot index (0 for SIM 1, 1 for SIM 2 on dual-SIM devices).
 * @param voiceServiceAvailable True if cellular voice service is currently available.
 * @param imsRegistered Future use: True if the SIM is IMS-registered (VoLTE capable).
 */
@Serializable
data class SimInfo(
    val subscriptionId: Int,
    val operatorName: String,
    val operatorType: OperatorType,
    val slotIndex: Int,
    val voiceServiceAvailable: Boolean,
    val imsRegistered: Boolean = false,
)
