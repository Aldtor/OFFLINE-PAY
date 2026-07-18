package com.offlinepay.feature.pay123

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for the 123PAY feature.
 *
 * [Pay123Controller] is annotated with [@Singleton][javax.inject.Singleton] and
 * uses constructor injection, so no explicit `@Provides` bindings are needed here.
 * The module declaration is retained as an extension point for future bindings.
 *
 * [Pay123ViewModel] is a HiltViewModel and is auto-provided by Hilt.
 *
 * Design reference: Section 7 (UPI 123PAY Engine Design)
 * Requirements: Req 6.1–6.8
 */
@Module
@InstallIn(SingletonComponent::class)
object Pay123Module
