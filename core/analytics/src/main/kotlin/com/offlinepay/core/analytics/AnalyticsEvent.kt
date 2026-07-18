package com.offlinepay.core.analytics

import android.os.Bundle
import com.offlinepay.core.domain.model.PaymentMethodType
import com.offlinepay.core.domain.model.QrType
import kotlinx.serialization.Serializable

/**
 * Sealed hierarchy representing all analytics events in the OfflinePay app.
 * No raw strings are used for event names to ensure compile-time safety.
 *
 * Design reference: Section 14.1
 */
@Serializable
sealed interface AnalyticsEvent {

    // ── QR Scanning ──
    @Serializable
    data object QrScanInitiated : AnalyticsEvent

    @Serializable
    data class QrScanSuccess(val qrType: QrType) : AnalyticsEvent

    @Serializable
    data class QrScanFailure(val reason: String) : AnalyticsEvent

    @Serializable
    data object QrScanFallbackToZxing : AnalyticsEvent

    // ── Payment Flow ──
    @Serializable
    data class PaymentAttempted(
        val method: PaymentMethodType,
        val operatorType: String
    ) : AnalyticsEvent

    @Serializable
    data class PaymentSuccess(
        val method: PaymentMethodType,
        val operatorType: String,
        val durationMs: Long
    ) : AnalyticsEvent

    @Serializable
    data class PaymentFailure(
        val method: PaymentMethodType,
        val operatorType: String,
        val reason: String
    ) : AnalyticsEvent

    @Serializable
    data class FallbackTriggered(
        val fromMethod: PaymentMethodType,
        val toMethod: PaymentMethodType,
        val reason: String
    ) : AnalyticsEvent

    @Serializable
    data class PaymentCancelled(val screen: String) : AnalyticsEvent

    // ── USSD-specific ──
    @Serializable
    data class UssdSessionStarted(val operatorType: String) : AnalyticsEvent

    @Serializable
    data class UssdSessionTimeout(val stepCount: Int) : AnalyticsEvent

    @Serializable
    data object UssdFallbackAccepted : AnalyticsEvent

    @Serializable
    data object UssdFallbackDeclined : AnalyticsEvent

    // ── 123PAY-specific ──
    @Serializable
    data class Pay123CallInitiated(val operatorType: String) : AnalyticsEvent

    @Serializable
    data object Pay123UserConfirmedSuccess : AnalyticsEvent

    @Serializable
    data object Pay123UserConfirmedFailure : AnalyticsEvent

    @Serializable
    data object Pay123UserDismissed : AnalyticsEvent

    // ── Permissions ──
    @Serializable
    data class PermissionGranted(val permission: String) : AnalyticsEvent

    @Serializable
    data class PermissionDenied(val permission: String, val isPermanent: Boolean) : AnalyticsEvent

    // ── Settings ──
    @Serializable
    data class SettingsChanged(val key: String) : AnalyticsEvent

    @Serializable
    data class RoutingOverrideSet(val method: PaymentMethodType) : AnalyticsEvent

    @Serializable
    data object RoutingOverrideCleared : AnalyticsEvent

    // ── App Lifecycle ──
    @Serializable
    data class AppStarted(val integrityTier: Int) : AnalyticsEvent

    @Serializable
    data object OnboardingCompleted : AnalyticsEvent

    @Serializable
    data class SecurityBannerShown(val tier: Int) : AnalyticsEvent

    @Serializable
    data class SecurityBannerDismissed(val tier: Int) : AnalyticsEvent
}

/**
 * Maps an [AnalyticsEvent] to a name for Firebase Analytics logging.
 */
fun AnalyticsEvent.toFirebaseEventName(): String {
    return this::class.simpleName ?: "unknown_event"
}

/**
 * Maps an [AnalyticsEvent] to a Map of parameter values.
 */
fun AnalyticsEvent.toFirebaseParamsMap(): Map<String, String> {
    val map = mutableMapOf<String, String>()
    when (this) {
        is AnalyticsEvent.QrScanSuccess -> {
            map["qr_type"] = qrType.name
        }
        is AnalyticsEvent.QrScanFailure -> {
            map["reason"] = reason
        }
        is AnalyticsEvent.PaymentAttempted -> {
            map["method"] = method.name
            map["operator_type"] = operatorType
        }
        is AnalyticsEvent.PaymentSuccess -> {
            map["method"] = method.name
            map["operator_type"] = operatorType
            map["duration_ms"] = durationMs.toString()
        }
        is AnalyticsEvent.PaymentFailure -> {
            map["method"] = method.name
            map["operator_type"] = operatorType
            map["reason"] = reason
        }
        is AnalyticsEvent.FallbackTriggered -> {
            map["from_method"] = fromMethod.name
            map["to_method"] = toMethod.name
            map["reason"] = reason
        }
        is AnalyticsEvent.PaymentCancelled -> {
            map["screen"] = screen
        }
        is AnalyticsEvent.UssdSessionStarted -> {
            map["operator_type"] = operatorType
        }
        is AnalyticsEvent.UssdSessionTimeout -> {
            map["step_count"] = stepCount.toString()
        }
        is AnalyticsEvent.Pay123CallInitiated -> {
            map["operator_type"] = operatorType
        }
        is AnalyticsEvent.PermissionGranted -> {
            map["permission"] = permission
        }
        is AnalyticsEvent.PermissionDenied -> {
            map["permission"] = permission
            map["is_permanent"] = isPermanent.toString()
        }
        is AnalyticsEvent.SettingsChanged -> {
            map["key"] = key
        }
        is AnalyticsEvent.RoutingOverrideSet -> {
            map["method"] = method.name
        }
        is AnalyticsEvent.AppStarted -> {
            map["integrity_tier"] = integrityTier.toString()
        }
        is AnalyticsEvent.SecurityBannerShown -> {
            map["tier"] = tier.toString()
        }
        is AnalyticsEvent.SecurityBannerDismissed -> {
            map["tier"] = tier.toString()
        }
        else -> {
            // Objects have no custom parameters
        }
    }
    return map
}

/**
 * Utility extension to convert a Map to a Bundle.
 */
fun Map<String, String>.toBundle(): Bundle {
    val bundle = Bundle()
    for ((key, value) in this) {
        bundle.putString(key, value)
    }
    return bundle
}
