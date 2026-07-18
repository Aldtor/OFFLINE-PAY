package com.offlinepay.core.common.result

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

/**
 * Unit tests for [AppResult] sealed class and its extension functions.
 *
 * Covers: Success, Failure, Loading states; map, flatMap, mapError, onSuccess,
 * onFailure, getOrNull, errorOrNull, getOrThrow, getOrDefault, appResultOf.
 */
class AppResultTest {

    // ── isSuccess / isFailure / isLoading ───────────────────────────────────

    @Test
    fun `Success isSuccess returns true`() {
        AppResult.Success(42).isSuccess shouldBe true
    }

    @Test
    fun `Failure isFailure returns true`() {
        AppResult.Failure("error").isFailure shouldBe true
    }

    @Test
    fun `Loading isLoading returns true`() {
        AppResult.Loading.isLoading shouldBe true
    }

    @Test
    fun `Success isFailure returns false`() {
        AppResult.Success(42).isFailure shouldBe false
    }

    @Test
    fun `Failure isSuccess returns false`() {
        AppResult.Failure("error").isSuccess shouldBe false
    }

    // ── map ─────────────────────────────────────────────────────────────────

    @Test
    fun `map transforms Success value`() {
        val result = AppResult.Success(10).map { it * 2 }
        result shouldBe AppResult.Success(20)
    }

    @Test
    fun `map leaves Failure unchanged`() {
        val result: AppResult<Int, String> = AppResult.Failure("err")
        result.map { it * 2 } shouldBe AppResult.Failure("err")
    }

    @Test
    fun `map leaves Loading unchanged`() {
        val result: AppResult<Int, String> = AppResult.Loading
        result.map { it * 2 }.shouldBeInstanceOf<AppResult.Loading>()
    }

    // ── flatMap ──────────────────────────────────────────────────────────────

    @Test
    fun `flatMap chains Success operations`() {
        val result = AppResult.Success(5)
            .flatMap { AppResult.Success(it * 3) }
        result shouldBe AppResult.Success(15)
    }

    @Test
    fun `flatMap propagates inner Failure`() {
        val initial: AppResult<Int, String> = AppResult.Success(5)
        val result: AppResult<Int, String> = initial.flatMap { _ ->
            AppResult.Failure("inner error")
        }
        result.isFailure shouldBe true
        result.errorOrNull() shouldBe "inner error"
    }

    @Test
    fun `flatMap does not execute on outer Failure`() {
        var executed = false
        val original: AppResult<Int, String> = AppResult.Failure("outer")
        val result = original.flatMap {
            executed = true
            AppResult.Success(it)
        }
        executed shouldBe false
        result shouldBe AppResult.Failure("outer")
    }

    // ── mapError ─────────────────────────────────────────────────────────────

    @Test
    fun `mapError transforms Failure error`() {
        val result = AppResult.Failure("string error").mapError { it.length }
        result shouldBe AppResult.Failure(12)
    }

    @Test
    fun `mapError leaves Success unchanged`() {
        val result = AppResult.Success(99).mapError { "unused" }
        result shouldBe AppResult.Success(99)
    }

    // ── getOrNull / errorOrNull ───────────────────────────────────────────────

    @Test
    fun `getOrNull returns data for Success`() {
        AppResult.Success("hello").getOrNull() shouldBe "hello"
    }

    @Test
    fun `getOrNull returns null for Failure`() {
        AppResult.Failure("error").getOrNull() shouldBe null
    }

    @Test
    fun `getOrNull returns null for Loading`() {
        AppResult.Loading.getOrNull() shouldBe null
    }

    @Test
    fun `errorOrNull returns error for Failure`() {
        AppResult.Failure(42).errorOrNull() shouldBe 42
    }

    @Test
    fun `errorOrNull returns null for Success`() {
        AppResult.Success("data").errorOrNull() shouldBe null
    }

    // ── getOrThrow ────────────────────────────────────────────────────────────

    @Test
    fun `getOrThrow returns data for Success`() {
        AppResult.Success(7).getOrThrow() shouldBe 7
    }

    @Test
    fun `getOrThrow throws for Failure`() {
        shouldThrow<NoSuchElementException> {
            AppResult.Failure("err").getOrThrow()
        }
    }

    @Test
    fun `getOrThrow throws for Loading`() {
        shouldThrow<NoSuchElementException> {
            AppResult.Loading.getOrThrow()
        }
    }

    // ── getOrDefault ──────────────────────────────────────────────────────────

    @Test
    fun `getOrDefault returns data for Success`() {
        AppResult.Success(5).getOrDefault(0) shouldBe 5
    }

    @Test
    fun `getOrDefault returns default for Failure`() {
        val result: AppResult<Int, String> = AppResult.Failure("error")
        result.getOrDefault(99) shouldBe 99
    }

    @Test
    fun `getOrDefault returns default for Loading`() {
        val result: AppResult<Int, String> = AppResult.Loading
        result.getOrDefault(42) shouldBe 42
    }

    // ── onSuccess / onFailure / onLoading ────────────────────────────────────

    @Test
    fun `onSuccess executes action for Success`() {
        var called = false
        AppResult.Success("data").onSuccess { called = true }
        called shouldBe true
    }

    @Test
    fun `onSuccess does not execute for Failure`() {
        var called = false
        AppResult.Failure("err").onSuccess { called = true }
        called shouldBe false
    }

    @Test
    fun `onFailure executes action for Failure`() {
        var captured: String? = null
        AppResult.Failure("error message").onFailure { captured = it }
        captured shouldBe "error message"
    }

    @Test
    fun `onLoading executes action for Loading`() {
        var called = false
        AppResult.Loading.onLoading { called = true }
        called shouldBe true
    }

    // ── appResultOf ───────────────────────────────────────────────────────────

    @Test
    fun `appResultOf returns Success when block succeeds`() {
        val result = appResultOf(errorMapper = { "err: ${it.message}" }) { 42 }
        result shouldBe AppResult.Success(42)
    }

    @Test
    fun `appResultOf returns Failure when block throws`() {
        val result = appResultOf(errorMapper = { it.message ?: "unknown" }) {
            throw IllegalStateException("boom")
        }
        result shouldBe AppResult.Failure("boom")
    }

    @Test
    fun `appResultOf preserves chaining with flatMap`() {
        val result = appResultOf(errorMapper = { "err" }) { 10 }
            .flatMap { AppResult.Success(it * 2) }
            .map { "Result: $it" }
        result shouldBe AppResult.Success("Result: 20")
    }
}
