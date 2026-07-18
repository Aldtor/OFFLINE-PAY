package com.offlinepay.core.telephony

import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.domain.error.DomainError
import com.offlinepay.core.domain.model.SimInfo
import com.offlinepay.core.domain.usecase.sim.DetectSimUseCase
import javax.inject.Inject

/**
 * Implementation of [DetectSimUseCase] that delegates to [SimDetector].
 *
 * Design reference: Section 3.3
 */
class DetectSimUseCaseImpl @Inject constructor(
    private val simDetector: SimDetector
) : DetectSimUseCase {

    override suspend fun invoke(): AppResult<List<SimInfo>, DomainError> {
        return simDetector.detectSims()
    }
}
