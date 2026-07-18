package com.offlinepay.feature.payment

import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.domain.error.DomainError
import com.offlinepay.core.domain.model.AppSettings
import com.offlinepay.core.domain.model.OperatorType
import com.offlinepay.core.domain.model.UserPreferences
import com.offlinepay.core.domain.payment.RoutingEngine
import com.offlinepay.core.domain.usecase.payment.GetRoutingDecisionUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for the `:feature:payment` module.
 *
 * Binds [GetRoutingDecisionUseCase] (a `fun interface`) to a lambda that delegates
 * to the [RoutingEngine] implementation.
 *
 * [RoutingEngine] is bound to [RoutingEngineImpl] in [PaymentEngineModule], which is
 * also installed in [SingletonComponent], so the binding is available here.
 *
 * [InitiatePaymentUseCase] is a full interface (not fun interface) and is not used
 * in the current ViewModels — [PaymentConfirmationViewModel] delegates directly to
 * [GetRoutingDecisionUseCase] then navigates. A concrete implementation can be
 * provided here when the full orchestration use case is wired.
 *
 * Design reference: Section 4.2 (Routing Algorithm), Section 3.3 (Use Cases)
 * Requirements: Req 4.1–4.11
 */
@Module
@InstallIn(SingletonComponent::class)
object PaymentModule {

    /**
     * Provides [GetRoutingDecisionUseCase] by wrapping [RoutingEngine.route].
     *
     * Converts [AppSettings] → [UserPreferences] for the engine call.
     * Returns [AppResult.Failure] with [DomainError.PaymentError.RoutingFailed] if the engine
     * throws, which should never happen in practice (the engine is pure logic).
     */
    @Provides
    @Singleton
    fun provideGetRoutingDecisionUseCase(
        routingEngine: RoutingEngine,
    ): GetRoutingDecisionUseCase = GetRoutingDecisionUseCase { simInfo, params, settings ->
        try {
            val prefs = settings.toUserPreferences()
            val decision = routingEngine.route(simInfo, params, prefs)
            AppResult.Success(decision)
        } catch (e: Exception) {
            AppResult.Failure(
                DomainError.PaymentError.RoutingFailed(
                    reason = e.message ?: "Routing engine error",
                )
            )
        }
    }
}

/**
 * Maps [AppSettings] to [UserPreferences] for the [RoutingEngine].
 * [AppSettings] is the domain model; [UserPreferences] is the engine's input type.
 */
private fun AppSettings.toUserPreferences(): UserPreferences = UserPreferences(
    manualPaymentMethodOverride = manualPaymentMethodOverride,
    routingPriorityMap = buildMap {
        val config = routingPriorityConfig
        put(com.offlinepay.core.domain.model.OperatorType.AIRTEL, config.airtelPriority)
        put(com.offlinepay.core.domain.model.OperatorType.VI, config.viPriority)
        put(com.offlinepay.core.domain.model.OperatorType.BSNL, config.bsnlPriority)
        put(com.offlinepay.core.domain.model.OperatorType.JIO, config.jioPriority)
        put(com.offlinepay.core.domain.model.OperatorType.OTHER, config.otherPriority)
    },
)
