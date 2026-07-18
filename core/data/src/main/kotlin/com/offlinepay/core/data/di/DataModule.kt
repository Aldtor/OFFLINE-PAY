package com.offlinepay.core.data.di

import android.content.Context
import com.offlinepay.core.common.dispatchers.DefaultDispatcher
import com.offlinepay.core.common.dispatchers.IoDispatcher
import com.offlinepay.core.common.dispatchers.MainDispatcher
import com.offlinepay.core.common.dispatchers.UnconfinedDispatcher
import com.offlinepay.core.common.dispatchers.AppDispatchers
import com.offlinepay.core.data.crypto.DatabaseKeyProvider
import com.offlinepay.core.data.crypto.EncryptedPreferencesProvider
import com.offlinepay.core.data.db.OfflinePayDatabase
import com.offlinepay.core.data.db.dao.MerchantDao
import com.offlinepay.core.data.db.dao.SettingsDao
import com.offlinepay.core.data.db.dao.TransactionDao
import com.offlinepay.core.data.db.dao.AnalyticsEventDao
import com.offlinepay.core.data.repository.IntegrityRepositoryImpl
import com.offlinepay.core.data.repository.MerchantRepositoryImpl
import com.offlinepay.core.data.repository.SettingsRepositoryImpl
import com.offlinepay.core.data.repository.TransactionRepositoryImpl
import com.offlinepay.core.domain.repository.IntegrityRepository
import com.offlinepay.core.domain.repository.MerchantRepository
import com.offlinepay.core.domain.repository.SettingsRepository
import com.offlinepay.core.domain.repository.TransactionRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

/**
 * Hilt module providing all data layer dependencies.
 *
 * Provides:
 * - [OfflinePayDatabase] (SQLCipher-encrypted, @Singleton)
 * - All Room DAOs
 * - [EncryptedPreferencesProvider] and [DatabaseKeyProvider]
 * - All [AppDispatchers] qualified dispatcher bindings
 * - Repository interface → implementation bindings
 *
 * Design reference: Section 3.4 (DataModule)
 * Requirements: Req 20.4 (Hilt DI), Req 20.7 (Single Source of Truth)
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    // ── Database ──────────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideOfflinePayDatabase(
        @ApplicationContext context: Context,
        keyProvider: DatabaseKeyProvider,
    ): OfflinePayDatabase = OfflinePayDatabase.create(context, keyProvider)

    @Provides
    @Singleton
    fun provideTransactionDao(db: OfflinePayDatabase): TransactionDao = db.transactionDao()

    @Provides
    @Singleton
    fun provideMerchantDao(db: OfflinePayDatabase): MerchantDao = db.merchantDao()

    @Provides
    @Singleton
    fun provideSettingsDao(db: OfflinePayDatabase): SettingsDao = db.settingsDao()

    @Provides
    @Singleton
    fun provideAnalyticsEventDao(db: OfflinePayDatabase): AnalyticsEventDao = db.analyticsEventDao()

    // ── Dispatchers ───────────────────────────────────────────────────────────

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main

    @Provides
    @UnconfinedDispatcher
    fun provideUnconfinedDispatcher(): CoroutineDispatcher = Dispatchers.Unconfined

    @Provides
    @Singleton
    fun provideAppDispatchers(
        @IoDispatcher io: CoroutineDispatcher,
        @DefaultDispatcher default: CoroutineDispatcher,
        @MainDispatcher main: CoroutineDispatcher,
        @UnconfinedDispatcher unconfined: CoroutineDispatcher,
    ): AppDispatchers = AppDispatchers(
        io = io,
        default = default,
        main = main,
        unconfined = unconfined,
    )
}

/**
 * Hilt module binding repository interfaces to their implementations.
 * Separated from [DataModule] to allow `@Binds` (which requires an abstract class/interface).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryBindingsModule {

    @Binds
    @Singleton
    abstract fun bindTransactionRepository(
        impl: TransactionRepositoryImpl,
    ): TransactionRepository

    @Binds
    @Singleton
    abstract fun bindMerchantRepository(
        impl: MerchantRepositoryImpl,
    ): MerchantRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: SettingsRepositoryImpl,
    ): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindIntegrityRepository(
        impl: IntegrityRepositoryImpl,
    ): IntegrityRepository
}
