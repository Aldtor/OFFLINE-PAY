package com.offlinepay.feature.ussd

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for the USSD feature.
 *
 * [UssdController] and [UssdResponseParser] are annotated with [@Singleton][javax.inject.Singleton]
 * and use constructor injection, so no explicit `@Provides` bindings are needed here.
 * The module declaration is retained as an extension point for future bindings.
 *
 * Design reference: Section 5 (USSD feature)
 */
@Module
@InstallIn(SingletonComponent::class)
object UssdModule
