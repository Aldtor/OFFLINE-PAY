package com.offlinepay.core.common.result

/**
 * A sealed result wrapper used throughout OfflinePay for representing
 * the outcome of any operation that can succeed, fail, or be in a loading state.
 *
 * All use cases and repositories return [AppResult] instead of throwing exceptions,
 * enabling structured, type-safe error handling across the entire application.
 *
 * Design reference: Section 3.5 (`:core:common`)
 *
 * @param T The type of the successful result value.
 * @param E The type of the error (typically a subclass of [com.offlinepay.core.domain.error.DomainError]).
 */
sealed class AppResult<out T, out E> {

    /**
     * Represents a successful operation with a non-null [data] value.
     *
     * @param data The result of the successful operation.
     */
    data class Success<out T>(val data: T) : AppResult<T, Nothing>()

    /**
     * Represents a failed operation with a typed [error] value.
     *
     * @param error The error that caused the failure.
     */
    data class Failure<out E>(val error: E) : AppResult<Nothing, E>()

    /**
     * Represents an in-progress operation.
     * Used in StateFlow UI state to signal that a result is being fetched.
     */
    data object Loading : AppResult<Nothing, Nothing>()

    // ── Utility properties ────────────────────────────────────────────────────

    /** Returns true if this result is [Success]. */
    val isSuccess: Boolean get() = this is Success

    /** Returns true if this result is [Failure]. */
    val isFailure: Boolean get() = this is Failure

    /** Returns true if this result is [Loading]. */
    val isLoading: Boolean get() = this is Loading

    // ── Transformation functions ───────────────────────────────────────────────

    /**
     * Transforms the [Success] value using [transform], leaving [Failure] and [Loading] unchanged.
     *
     * @param transform A function applied to the successful data.
     * @return A new [AppResult] with the transformed value, or the original [Failure]/[Loading].
     */
    inline fun <R> map(transform: (T) -> R): AppResult<R, E> = when (this) {
        is Success -> Success(transform(data))
        is Failure -> this
        is Loading -> this
    }

    /**
     * Transforms the [Success] value using [transform] which itself returns an [AppResult],
     * effectively chaining operations. Leaves [Failure] and [Loading] unchanged.
     *
     * @param transform A function returning an [AppResult] applied to the successful data.
     * @return The [AppResult] returned by [transform], or the original [Failure]/[Loading].
     */
    @Suppress("UNCHECKED_CAST")
    inline fun <R> flatMap(transform: (T) -> AppResult<R, @UnsafeVariance E>): AppResult<R, E> =
        when (this) {
            is Success -> transform(data)
            is Failure -> this as AppResult<R, E>
            is Loading -> this as AppResult<R, E>
        }

    /**
     * Transforms the [Failure] error using [transform], leaving [Success] and [Loading] unchanged.
     *
     * @param transform A function applied to the error value.
     * @return A new [AppResult] with the transformed error, or the original [Success]/[Loading].
     */
    inline fun <F> mapError(transform: (E) -> F): AppResult<T, F> = when (this) {
        is Success -> this
        is Failure -> Failure(transform(error))
        is Loading -> this
    }

    // ── Extraction functions ───────────────────────────────────────────────────

    /**
     * Returns the [Success] data value, or null if this is [Failure] or [Loading].
     */
    fun getOrNull(): T? = (this as? Success)?.data

    /**
     * Returns the [Failure] error, or null if this is [Success] or [Loading].
     */
    fun errorOrNull(): E? = (this as? Failure)?.error

    /**
     * Returns the [Success] data value, or throws [NoSuchElementException] if not [Success].
     *
     * @throws NoSuchElementException if this is [Failure] or [Loading].
     */
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Failure -> throw NoSuchElementException("AppResult is Failure: $error")
        is Loading -> throw NoSuchElementException("AppResult is Loading")
    }

    /**
     * Returns the [Success] data value, or [default] if this is [Failure] or [Loading].
     *
     * @param default The fallback value.
     */
    fun getOrDefault(default: @UnsafeVariance T): T = (this as? Success)?.data ?: default

    // ── Side-effect functions ─────────────────────────────────────────────────

    /**
     * Executes [action] on the [Success] data. Returns this [AppResult] unchanged.
     */
    inline fun onSuccess(action: (T) -> Unit): AppResult<T, E> {
        if (this is Success) action(data)
        return this
    }

    /**
     * Executes [action] on the [Failure] error. Returns this [AppResult] unchanged.
     */
    inline fun onFailure(action: (E) -> Unit): AppResult<T, E> {
        if (this is Failure) action(error)
        return this
    }

    /**
     * Executes [action] when in [Loading] state. Returns this [AppResult] unchanged.
     */
    inline fun onLoading(action: () -> Unit): AppResult<T, E> {
        if (this is Loading) action()
        return this
    }
}

/**
 * Wraps a suspending [block] in an [AppResult], catching any [Exception] and
 * mapping it using [errorMapper].
 *
 * Usage:
 * ```kotlin
 * val result = runCatching(errorMapper = { StorageError.DatabaseError(it.message) }) {
 *     dao.getAll()
 * }
 * ```
 *
 * @param errorMapper Maps a caught [Exception] to the error type [E].
 * @param block The suspending operation to execute.
 * @return [AppResult.Success] if [block] completes normally, [AppResult.Failure] if it throws.
 */
inline fun <T, E> appResultOf(
    errorMapper: (Exception) -> E,
    block: () -> T,
): AppResult<T, E> = try {
    AppResult.Success(block())
} catch (e: Exception) {
    AppResult.Failure(errorMapper(e))
}
