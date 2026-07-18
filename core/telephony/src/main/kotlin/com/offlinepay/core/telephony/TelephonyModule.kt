package com.offlinepay.core.telephony

import android.content.Context
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.offlinepay.core.domain.usecase.sim.DetectSimUseCase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing telephony infrastructure dependencies and use case bindings.
 *
 * Design reference: Section 3.9
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TelephonyModule {

    @Binds
    @Singleton
    abstract fun bindDetectSimUseCase(
        impl: DetectSimUseCaseImpl
    ): DetectSimUseCase

    companion object {

        @Provides
        @Singleton
        fun provideTelephonyManager(
            @ApplicationContext context: Context
        ): TelephonyManager {
            return context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        }

        @Provides
        @Singleton
        fun provideSubscriptionManager(
            @ApplicationContext context: Context
        ): SubscriptionManager {
            return context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
        }
    }
}
