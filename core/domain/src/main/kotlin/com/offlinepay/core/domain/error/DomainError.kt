package com.offlinepay.core.domain.error

/**
 * Root sealed class for all domain-level errors in OfflinePay.
 *
 * Every use case and repository method returns `AppResult<T, DomainError>`.
 * This hierarchy maps directly to the UI error taxonomy in Design Section 13.2.
 * Each variant has enough context to produce a user-facing [ErrorUiState].
 *
 * Design reference: Section 13.1 (Error Taxonomy)
 * Requirements: Req 12 (Error Handling and User Feedback)
 */
sealed class DomainError {

    // ── QR Errors ─────────────────────────────────────────────────────────────

    /**
     * Errors produced by the QR parser pipeline.
     * Design: Section 5.3 (UPI URI Parser Error Taxonomy)
     */
    sealed class QrError : DomainError() {

        /** QR string does not match any supported UPI URI format. */
        data class InvalidFormat(
            val rawContent: String,
            val reason: String,
        ) : QrError()

        /**
         * QR content is valid but not a UPI URI (URL, vCard, WiFi config, etc.).
         * [detectedType] is shown to the user (e.g., "URL", "Contact", "Wi-Fi").
         */
        data class NonUpiContent(val detectedType: String) : QrError()

        /** A mandatory UPI URI field is missing from the decoded QR. */
        data class MissingMandatoryField(val fieldName: String) : QrError()

        /** The `am` field in the QR is invalid or outside NPCI limits. */
        data class InvalidAmount(
            val rawValue: String,
            val reason: AmountErrorReason,
        ) : QrError()

        /** Bharat QR detected but UPI fields are absent. */
        data class UnsupportedBharatQr(val reason: String) : QrError()

        /** The decoded string could not be parsed as a URI. */
        data class MalformedUri(val rawString: String) : QrError()
    }

    /** Reason for an invalid amount in a QR code. */
    enum class AmountErrorReason {
        NEGATIVE, ZERO, EXCEEDS_LIMIT, NON_NUMERIC, TOO_MANY_DECIMALS
    }

    // ── Payment Errors ────────────────────────────────────────────────────────

    /**
     * Errors produced by the Offline Payment Engine.
     * Design: Section 4.2 (Routing Algorithm), Section 13.1
     */
    sealed class PaymentError : DomainError() {

        /** No SIM card is inserted in the device (Req 3.5). */
        data object NoSim : PaymentError()

        /**
         * The selected SIM has no cellular voice service.
         * @param simSlotIndex The SIM slot that has no service.
         * @param operator The operator name for display.
         */
        data class NoService(val simSlotIndex: Int, val operator: String) : PaymentError()

        /** The routing engine could not find any capable payment strategy. */
        data class RoutingFailed(val reason: String) : PaymentError()

        /** All payment methods were attempted and all failed. */
        data class AllMethodsFailed(
            val attemptedMethods: List<com.offlinepay.core.domain.model.PaymentMethodType>,
        ) : PaymentError()
    }

    // ── USSD Errors ───────────────────────────────────────────────────────────

    /**
     * Errors produced by the USSD Controller.
     * Design: Section 6.4 (USSD Failure Modes)
     * Requirements: Req 5
     */
    sealed class UssdError : DomainError() {

        /**
         * CALL_PHONE permission was denied.
         * @param showRationale True if the rationale dialog should be shown.
         */
        data class PermissionDenied(val showRationale: Boolean) : UssdError()

        /**
         * USSD session timed out with no response.
         * @param sessionStepCount Number of menu steps completed before timeout.
         */
        data class Timeout(val sessionStepCount: Int) : UssdError()

        /**
         * Network-level USSD failure.
         * @param failureCode Android telephony failure code, if available.
         */
        data class NetworkError(val failureCode: Int? = null) : UssdError()

        /**
         * Bank declined the payment via USSD response.
         * @param sanitisedMessage Error text with PII stripped (Req 9.17).
         */
        data class BankDeclined(val sanitisedMessage: String) : UssdError()

        /** User cancelled the active USSD session (Req 5.7). */
        data object Cancelled : UssdError()

        /** The selected operator does not support *99# USSD. */
        data class OperatorUnsupported(val operator: String) : UssdError()
    }

    // ── 123PAY Errors ─────────────────────────────────────────────────────────

    /**
     * Errors produced by the 123PAY Controller.
     * Design: Section 7.4 (123PAY Failure Modes)
     * Requirements: Req 6
     */
    sealed class Pay123Error : DomainError() {

        /**
         * The ACTION_CALL intent failed to launch.
         * @param reason Human-readable reason for display.
         */
        data class CallFailed(val reason: String) : Pay123Error()

        /** No cellular service available to place the 123PAY IVR call (Req 6.1). */
        data object NoService : Pay123Error()

        /** User cancelled before confirming the result. */
        data object UserCancelled : Pay123Error()
    }

    // ── Storage Errors ────────────────────────────────────────────────────────

    /**
     * Errors produced by the local Room database or EncryptedSharedPreferences.
     * Design: Section 13.1
     * Requirements: Req 8.5 (error state on history load failure)
     */
    sealed class StorageError : DomainError() {

        /**
         * A Room database operation failed.
         * @param operation The DAO method name that failed.
         * @param cause Sanitised exception message.
         */
        data class DatabaseError(val operation: String, val cause: String) : StorageError()

        /** Encryption or decryption operation failed. */
        data class EncryptionError(val cause: String) : StorageError()

        /**
         * Key rotation failed at a specific phase.
         * @param phase Description of which phase failed (generate/re-encrypt/swap/delete).
         * @param cause Sanitised exception message.
         */
        data class KeyRotationError(val phase: String, val cause: String) : StorageError()

        /**
         * Room database migration failed.
         * @param fromVersion The schema version before migration.
         * @param toVersion The target schema version.
         */
        data class MigrationError(val fromVersion: Int, val toVersion: Int) : StorageError()
    }

    // ── Security Errors ───────────────────────────────────────────────────────

    /**
     * Errors produced by the Security Guard.
     * Design: Section 8.3 (Runtime Protection Stack)
     * Requirements: Req 9.12–9.14
     */
    sealed class SecurityError : DomainError() {

        /**
         * App or device tampering was detected.
         * @param source Which detector triggered this error.
         */
        data class TamperDetected(val source: TamperSource) : SecurityError()

        /**
         * Play Integrity API returned a FAIL verdict.
         * @param verdictDetails Sanitised verdict details for logging.
         */
        data class IntegrityFailed(val verdictDetails: String) : SecurityError()

        /** A debugger was detected attached to the process in a release build (Req 9.13). */
        data object DebuggerDetected : SecurityError()
    }

    /** Source of a tamper detection event. */
    enum class TamperSource {
        CERTIFICATE_MISMATCH,
        FRIDA_DETECTED,
        XPOSED_DETECTED,
    }

    // ── Permission Errors ─────────────────────────────────────────────────────

    /**
     * Errors produced when a required runtime permission has been denied.
     * Design: Section 13.1
     * Requirements: Req 12.6 (permission denial error screen)
     */
    sealed class PermissionError : DomainError() {

        /** Camera permission is required for QR scanning (Req 2.7). */
        data class CameraRequired(val isPermanentlyDenied: Boolean) : PermissionError()

        /** CALL_PHONE permission is required for USSD (Req 5.1). */
        data class PhoneRequired(val isPermanentlyDenied: Boolean) : PermissionError()

        /** READ_PHONE_STATE permission is required for SIM detection (Req 3.4). */
        data class ReadPhoneStateRequired(val isPermanentlyDenied: Boolean) : PermissionError()
    }
}
