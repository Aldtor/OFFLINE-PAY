package com.offlinepay.core.analytics

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing bindings for analytics tracking.
 *
 * Design reference: Section 3.7
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AnalyticsModule {

    @Binds
    @Singleton
    abstract fun bindAnalyticsTracker(
        impl: FirebaseAnalyticsTracker
    ): AnalyticsTracker
}
