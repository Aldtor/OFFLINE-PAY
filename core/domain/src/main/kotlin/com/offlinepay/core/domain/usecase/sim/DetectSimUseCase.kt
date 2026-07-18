package com.offlinepay.core.domain.usecase.sim

import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.domain.error.DomainError
import com.offlinepay.core.domain.model.SimInfo

/**
 * Use case for detecting active SIM subscriptions on the device.
 *
 * Abstracts the `SimDetector` in `:core:telephony` so ViewModels depend only
 * on this interface, not on telephony Android APIs directly.
 *
 * Design reference: Section 3.3 (Use Cases), Section 3.9 (`:core:telephony`)
 * Requirements: Req 3.1–3.7 (SIM detection and operator identification)
 */
fun interface DetectSimUseCase {

    /**
     * Returns the list of active [SimInfo] subscriptions detected on the device.
     *
     * @return [AppResult.Success] with a non-empty list of [SimInfo].
     *         [AppResult.Failure] with [DomainError.PaymentError.NoSim] if no SIM is present.
     *         [AppResult.Failure] with [DomainError.PermissionError.ReadPhoneStateRequired]
     *           if the READ_PHONE_STATE permission is denied.
     */
    suspend operator fun invoke(): AppResult<List<SimInfo>, DomainError>
}
