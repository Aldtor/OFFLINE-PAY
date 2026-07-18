package com.offlinepay.core.common.dispatchers

import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Qualifier annotation for injecting the IO [CoroutineDispatcher].
 *
 * Use for database operations, file I/O, and network requests.
 * Backed by [Dispatchers.IO] in production, [kotlinx.coroutines.test.UnconfinedTestDispatcher]
 * or [kotlinx.coroutines.test.StandardTestDispatcher] in tests.
 *
 * Design reference: Section 3.5 (`:core:common` — AppDispatchers)
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.FIELD,
    AnnotationTarget.PROPERTY_GETTER,
)
annotation class IoDispatcher

/**
 * Qualifier annotation for injecting the Default (CPU-intensive) [CoroutineDispatcher].
 *
 * Use for computation, sorting, parsing, and mapping operations.
 * Backed by [Dispatchers.Default] in production.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.FIELD,
    AnnotationTarget.PROPERTY_GETTER,
)
annotation class DefaultDispatcher

/**
 * Qualifier annotation for injecting the Main (UI thread) [CoroutineDispatcher].
 *
 * Use when you need to update the UI or interact with main-thread-only APIs.
 * Backed by [Dispatchers.Main] in production.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.FIELD,
    AnnotationTarget.PROPERTY_GETTER,
)
annotation class MainDispatcher

/**
 * Qualifier annotation for injecting the Unconfined [CoroutineDispatcher].
 *
 * Used primarily in unit tests. Not recommended for production code outside of testing.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.FIELD,
    AnnotationTarget.PROPERTY_GETTER,
)
annotation class UnconfinedDispatcher

/**
 * Hilt-injectable wrapper providing all [CoroutineDispatcher] variants used in OfflinePay.
 *
 * Inject this class (or individual qualified dispatchers) instead of using [Dispatchers] directly.
 * This allows test code to substitute test dispatchers without modifying production code.
 *
 * ### Usage in production
 * ```kotlin
 * @HiltViewModel
 * class MyViewModel @Inject constructor(
 *     private val dispatchers: AppDispatchers,
 * ) : ViewModel() {
 *     fun loadData() = viewModelScope.launch(dispatchers.io) { ... }
 * }
 * ```
 *
 * ### Usage in tests
 * ```kotlin
 * val testDispatcher = StandardTestDispatcher()
 * val dispatchers = AppDispatchers(
 *     io = testDispatcher,
 *     default = testDispatcher,
 *     main = testDispatcher,
 *     unconfined = testDispatcher,
 * )
 * ```
 *
 * Design reference: Section 3.5 (`:core:common`)
 *
 * @param io Dispatcher for I/O-bound operations (default: [Dispatchers.IO]).
 * @param default Dispatcher for CPU-bound operations (default: [Dispatchers.Default]).
 * @param main Dispatcher for main-thread operations (default: [Dispatchers.Main]).
 * @param unconfined Unconfined dispatcher for testing (default: [Dispatchers.Unconfined]).
 */
data class AppDispatchers(
    @IoDispatcher val io: CoroutineDispatcher = Dispatchers.IO,
    @DefaultDispatcher val default: CoroutineDispatcher = Dispatchers.Default,
    @MainDispatcher val main: CoroutineDispatcher = Dispatchers.Main,
    @UnconfinedDispatcher val unconfined: CoroutineDispatcher = Dispatchers.Unconfined,
)
