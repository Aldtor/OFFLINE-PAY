package com.offlinepay.core.security.di

import com.offlinepay.core.domain.usecase.integrity.RefreshIntegrityVerdictUseCase
import com.offlinepay.core.security.integrity.RefreshIntegrityVerdictUseCaseImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for the security module.
 *
 * [SecurityGuard], [CertificateVerifier], [AntiDebugGuard], [FridaDetector],
 * [AccessibilityAbuseDetector], [IntegrityApiClient], [VerdictParser],
 * and [IntegrityVerdictCache] are all provided via @Inject constructors with @Singleton scope.
 *
 * [IntegrityRefreshWorker] is provided via @HiltWorker / @AssistedInject.
 *
 * This module binds the [RefreshIntegrityVerdictUseCase] fun interface to its
 * concrete implementation [RefreshIntegrityVerdictUseCaseImpl].
 *
 * Design reference: Section 3.6 (`:core:security`)
 * Requirements: Req 9.1–9.15
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {

    @Binds
    @Singleton
    abstract fun bindRefreshIntegrityVerdictUseCase(
        impl: RefreshIntegrityVerdictUseCaseImpl,
    ): RefreshIntegrityVerdictUseCase
}
