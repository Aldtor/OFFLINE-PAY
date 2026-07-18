package com.offlinepay.core.data.di

import com.offlinepay.core.domain.repository.IntegrityRepository
import com.offlinepay.core.domain.repository.MerchantRepository
import com.offlinepay.core.domain.repository.SettingsRepository
import com.offlinepay.core.domain.repository.TransactionRepository
import com.offlinepay.core.domain.usecase.integrity.DefaultGetIntegrityVerdictUseCase
import com.offlinepay.core.domain.usecase.integrity.GetIntegrityVerdictUseCase
import com.offlinepay.core.domain.usecase.merchant.GetFavouriteMerchantsUseCase
import com.offlinepay.core.domain.usecase.merchant.GetMerchantByUpiIdUseCase
import com.offlinepay.core.domain.usecase.merchant.GetRecentMerchantsUseCase
import com.offlinepay.core.domain.usecase.merchant.ToggleMerchantFavouriteUseCase
import com.offlinepay.core.domain.usecase.merchant.UpsertMerchantUseCase
import com.offlinepay.core.domain.usecase.settings.GetSettingsUseCase
import com.offlinepay.core.domain.usecase.settings.UpdateSettingsUseCase
import com.offlinepay.core.domain.usecase.transaction.DeleteTransactionUseCase
import com.offlinepay.core.domain.usecase.transaction.ExportTransactionsUseCase
import com.offlinepay.core.domain.usecase.transaction.GetThisMonthTransactionsUseCase
import com.offlinepay.core.domain.usecase.transaction.GetTodayTransactionsUseCase
import com.offlinepay.core.domain.usecase.transaction.GetTransactionByIdUseCase
import com.offlinepay.core.domain.usecase.transaction.GetTransactionHistoryUseCase
import com.offlinepay.core.domain.usecase.transaction.SaveTransactionUseCase
import com.offlinepay.core.domain.usecase.transaction.UpdateTransactionStatusUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing all domain use case instances.
 *
 * Because `:core:domain` is a pure Kotlin JVM module with zero Android/Hilt dependencies,
 * its classes CANNOT have `@Inject` constructors. All use cases are provided here via
 * `@Provides` lambdas that delegate to the injected repository implementations.
 *
 * `fun interface` use cases are provided as SAM lambda instances.
 * Plain class use cases are provided by constructing them with the injected repo.
 *
 * Note: [GetRoutingDecisionUseCase] is provided in `:feature:payment:PaymentModule` since
 * it depends on [RoutingEngine] which lives in the feature layer.
 *
 * Design reference: Section 3.3 (Use Cases), Section 3.4 (DataModule)
 * Requirements: Req 8, Req 18
 */
@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    // ── Integrity ─────────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideGetIntegrityVerdictUseCase(
        integrityRepository: IntegrityRepository,
    ): GetIntegrityVerdictUseCase = DefaultGetIntegrityVerdictUseCase(integrityRepository)

    // ── Transaction — fun interface types ─────────────────────────────────────

    @Provides
    @Singleton
    fun provideGetTransactionByIdUseCase(
        repo: TransactionRepository,
    ): GetTransactionByIdUseCase = GetTransactionByIdUseCase { id ->
        repo.getTransactionById(id)
    }

    @Provides
    @Singleton
    fun provideGetTransactionHistoryUseCase(
        repo: TransactionRepository,
    ): GetTransactionHistoryUseCase = GetTransactionHistoryUseCase { filters ->
        repo.getTransactionsPaged(filters)
    }

    @Provides
    @Singleton
    fun provideSaveTransactionUseCase(
        repo: TransactionRepository,
    ): SaveTransactionUseCase = SaveTransactionUseCase { record ->
        repo.saveTransaction(record)
    }

    @Provides
    @Singleton
    fun provideUpdateTransactionStatusUseCase(
        repo: TransactionRepository,
    ): UpdateTransactionStatusUseCase = UpdateTransactionStatusUseCase { id, status ->
        repo.updateTransactionStatus(id, status)
    }

    @Provides
    @Singleton
    fun provideExportTransactionsUseCase(
        repo: TransactionRepository,
    ): ExportTransactionsUseCase = ExportTransactionsUseCase { filters ->
        repo.getTransactionsForExport(filters)
    }

    // ── Transaction — plain class types ──────────────────────────────────────

    @Provides
    @Singleton
    fun provideDeleteTransactionUseCase(
        repo: TransactionRepository,
    ): DeleteTransactionUseCase = DeleteTransactionUseCase(repo)

    @Provides
    @Singleton
    fun provideGetTodayTransactionsUseCase(
        repo: TransactionRepository,
    ): GetTodayTransactionsUseCase = GetTodayTransactionsUseCase(repo)

    @Provides
    @Singleton
    fun provideGetThisMonthTransactionsUseCase(
        repo: TransactionRepository,
    ): GetThisMonthTransactionsUseCase = GetThisMonthTransactionsUseCase(repo)

    // ── Merchant — fun interface types ────────────────────────────────────────

    @Provides
    @Singleton
    fun provideGetMerchantByUpiIdUseCase(
        repo: MerchantRepository,
    ): GetMerchantByUpiIdUseCase = GetMerchantByUpiIdUseCase { upiId ->
        repo.getMerchantByUpiId(upiId)
    }

    // ── Merchant — plain class types ──────────────────────────────────────────

    @Provides
    @Singleton
    fun provideGetFavouriteMerchantsUseCase(
        repo: MerchantRepository,
    ): GetFavouriteMerchantsUseCase = GetFavouriteMerchantsUseCase(repo)

    @Provides
    @Singleton
    fun provideGetRecentMerchantsUseCase(
        repo: MerchantRepository,
    ): GetRecentMerchantsUseCase = GetRecentMerchantsUseCase(repo)

    @Provides
    @Singleton
    fun provideToggleMerchantFavouriteUseCase(
        repo: MerchantRepository,
    ): ToggleMerchantFavouriteUseCase = ToggleMerchantFavouriteUseCase(repo)

    @Provides
    @Singleton
    fun provideUpsertMerchantUseCase(
        repo: MerchantRepository,
    ): UpsertMerchantUseCase = UpsertMerchantUseCase(repo)

    // ── Settings — plain class types ──────────────────────────────────────────

    @Provides
    @Singleton
    fun provideGetSettingsUseCase(
        repo: SettingsRepository,
    ): GetSettingsUseCase = GetSettingsUseCase(repo)

    @Provides
    @Singleton
    fun provideUpdateSettingsUseCase(
        repo: SettingsRepository,
    ): UpdateSettingsUseCase = UpdateSettingsUseCase(repo)
}
