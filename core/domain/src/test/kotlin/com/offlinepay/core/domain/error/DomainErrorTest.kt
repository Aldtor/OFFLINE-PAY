package com.offlinepay.core.domain.error

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

/**
 * Unit tests for [DomainError] sealed hierarchy correctness.
 *
 * Verifies that all variants can be created, pattern-matched, and carry correct data.
 */
class DomainErrorTest {

    // ── QrError ───────────────────────────────────────────────────────────────

    @Test
    fun `QrError InvalidFormat carries raw content and reason`() {
        val error = DomainError.QrError.InvalidFormat("raw", "not a UPI URI")
        error.rawContent shouldBe "raw"
        error.reason shouldBe "not a UPI URI"
    }

    @Test
    fun `QrError NonUpiContent carries detected type`() {
        val error = DomainError.QrError.NonUpiContent("URL")
        error.detectedType shouldBe "URL"
    }

    @Test
    fun `QrError MissingMandatoryField carries field name`() {
        val error = DomainError.QrError.MissingMandatoryField("pa")
        error.fieldName shouldBe "pa"
    }

    @Test
    fun `QrError InvalidAmount carries raw value and reason`() {
        val error = DomainError.QrError.InvalidAmount("-100", DomainError.AmountErrorReason.NEGATIVE)
        error.rawValue shouldBe "-100"
        error.reason shouldBe DomainError.AmountErrorReason.NEGATIVE
    }

    // ── PaymentError ──────────────────────────────────────────────────────────

    @Test
    fun `PaymentError NoSim is a singleton object`() {
        val error: DomainError = DomainError.PaymentError.NoSim
        error.shouldBeInstanceOf<DomainError.PaymentError.NoSim>()
    }

    @Test
    fun `PaymentError NoService carries slot and operator`() {
        val error = DomainError.PaymentError.NoService(simSlotIndex = 1, operator = "Airtel")
        error.simSlotIndex shouldBe 1
        error.operator shouldBe "Airtel"
    }

    @Test
    fun `PaymentError AllMethodsFailed carries attempted methods`() {
        val error = DomainError.PaymentError.AllMethodsFailed(
            listOf(
                com.offlinepay.core.domain.model.PaymentMethodType.USSD,
                com.offlinepay.core.domain.model.PaymentMethodType.PAY123,
            )
        )
        error.attemptedMethods.size shouldBe 2
    }

    // ── UssdError ─────────────────────────────────────────────────────────────

    @Test
    fun `UssdError PermissionDenied carries showRationale flag`() {
        val error = DomainError.UssdError.PermissionDenied(showRationale = true)
        error.showRationale shouldBe true
    }

    @Test
    fun `UssdError Timeout carries stepCount`() {
        val error = DomainError.UssdError.Timeout(sessionStepCount = 3)
        error.sessionStepCount shouldBe 3
    }

    @Test
    fun `UssdError Cancelled is a singleton object`() {
        val error: DomainError = DomainError.UssdError.Cancelled
        error.shouldBeInstanceOf<DomainError.UssdError.Cancelled>()
    }

    @Test
    fun `UssdError NetworkError carries null failure code by default`() {
        val error = DomainError.UssdError.NetworkError()
        error.failureCode shouldBe null
    }

    // ── Pay123Error ───────────────────────────────────────────────────────────

    @Test
    fun `Pay123Error CallFailed carries reason`() {
        val error = DomainError.Pay123Error.CallFailed("ActivityNotFoundException")
        error.reason shouldBe "ActivityNotFoundException"
    }

    @Test
    fun `Pay123Error NoService is a singleton object`() {
        val error: DomainError = DomainError.Pay123Error.NoService
        error.shouldBeInstanceOf<DomainError.Pay123Error.NoService>()
    }

    // ── StorageError ──────────────────────────────────────────────────────────

    @Test
    fun `StorageError DatabaseError carries operation and cause`() {
        val error = DomainError.StorageError.DatabaseError("getById", "no such row")
        error.operation shouldBe "getById"
        error.cause shouldBe "no such row"
    }

    @Test
    fun `StorageError MigrationError carries from and to versions`() {
        val error = DomainError.StorageError.MigrationError(fromVersion = 1, toVersion = 2)
        error.fromVersion shouldBe 1
        error.toVersion shouldBe 2
    }

    // ── SecurityError ─────────────────────────────────────────────────────────

    @Test
    fun `SecurityError TamperDetected carries source`() {
        val error = DomainError.SecurityError.TamperDetected(DomainError.TamperSource.FRIDA_DETECTED)
        error.source shouldBe DomainError.TamperSource.FRIDA_DETECTED
    }

    @Test
    fun `SecurityError DebuggerDetected is a singleton object`() {
        val error: DomainError = DomainError.SecurityError.DebuggerDetected
        error.shouldBeInstanceOf<DomainError.SecurityError.DebuggerDetected>()
    }

    // ── PermissionError ───────────────────────────────────────────────────────

    @Test
    fun `PermissionError CameraRequired carries isPermanentlyDenied`() {
        val error = DomainError.PermissionError.CameraRequired(isPermanentlyDenied = true)
        error.isPermanentlyDenied shouldBe true
    }

    @Test
    fun `PermissionError PhoneRequired carries isPermanentlyDenied false`() {
        val error = DomainError.PermissionError.PhoneRequired(isPermanentlyDenied = false)
        error.isPermanentlyDenied shouldBe false
    }

    // ── Pattern matching completeness ─────────────────────────────────────────

    @Test
    fun `DomainError sealed class can be pattern matched exhaustively`() {
        val errors: List<DomainError> = listOf(
            DomainError.QrError.NonUpiContent("URL"),
            DomainError.PaymentError.NoSim,
            DomainError.UssdError.Cancelled,
            DomainError.Pay123Error.NoService,
            DomainError.StorageError.EncryptionError("key error"),
            DomainError.SecurityError.DebuggerDetected,
            DomainError.PermissionError.CameraRequired(false),
        )
        // Each error should be a DomainError — compile-time exhaustiveness check
        errors.forEach { error ->
            when (error) {
                is DomainError.QrError -> Unit
                is DomainError.PaymentError -> Unit
                is DomainError.UssdError -> Unit
                is DomainError.Pay123Error -> Unit
                is DomainError.StorageError -> Unit
                is DomainError.SecurityError -> Unit
                is DomainError.PermissionError -> Unit
            }
        }
        errors.size shouldBe 7
    }
}
