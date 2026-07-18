package com.offlinepay.core.domain.usecase

import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.domain.error.DomainError
import com.offlinepay.core.domain.fake.FakeIntegrityRepository
import com.offlinepay.core.domain.model.IntegrityVerdict
import com.offlinepay.core.domain.model.VerdictType
import com.offlinepay.core.domain.usecase.integrity.DefaultGetIntegrityVerdictUseCase
import com.offlinepay.core.domain.usecase.integrity.IntegrityVerdictResult
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [DefaultGetIntegrityVerdictUseCase] — Task 5.5.
 *
 * Note: [RefreshIntegrityVerdictUseCase] is an interface-only declaration in this module;
 * its concrete implementation lives in :core:security and is tested there.
 */
class IntegrityUseCaseTest {

    private lateinit var repository: FakeIntegrityRepository
    private lateinit var getIntegrityVerdict: DefaultGetIntegrityVerdictUseCase

    @BeforeEach
    fun setUp() {
        repository = FakeIntegrityRepository()
        getIntegrityVerdict = DefaultGetIntegrityVerdictUseCase(repository)
    }

    @Test
    fun `returns null verdict with MAX_VALUE age when cache is empty`() = runTest {
        val result = getIntegrityVerdict()
        result.shouldBeInstanceOf<AppResult.Success<IntegrityVerdictResult>>()
        val data = (result as AppResult.Success).data
        data.verdict shouldBe null
        data.ageMs shouldBe Long.MAX_VALUE
    }

    @Test
    fun `returns cached PASS verdict with correct age`() = runTest {
        repository.setCurrentTime(1_000_000L)
        repository.saveVerdict(IntegrityVerdict.pass(cachedAtMs = 1_000_000L))
        // Advance time by 1 hour
        repository.setCurrentTime(1_000_000L + 3_600_000L)

        val result = getIntegrityVerdict()
        result.shouldBeInstanceOf<AppResult.Success<IntegrityVerdictResult>>()
        val data = (result as AppResult.Success).data
        data.verdict?.verdict shouldBe VerdictType.PASS
        data.ageMs shouldBe 3_600_000L
    }

    @Test
    fun `returns cached FAIL verdict`() = runTest {
        repository.saveVerdict(IntegrityVerdict.fail(cachedAtMs = 0L))

        val result = getIntegrityVerdict()
        result.shouldBeInstanceOf<AppResult.Success<IntegrityVerdictResult>>()
        val data = (result as AppResult.Success).data
        data.verdict?.verdict shouldBe VerdictType.FAIL
    }

    @Test
    fun `age is zero immediately after caching`() = runTest {
        repository.setCurrentTime(5_000_000L)
        repository.saveVerdict(IntegrityVerdict.pass(cachedAtMs = 5_000_000L))

        val result = getIntegrityVerdict()
        result.shouldBeInstanceOf<AppResult.Success<IntegrityVerdictResult>>()
        (result as AppResult.Success).data.ageMs shouldBe 0L
    }

    @Test
    fun `age is MAX_VALUE when no verdict is cached`() = runTest {
        val result = getIntegrityVerdict()
        result.shouldBeInstanceOf<AppResult.Success<IntegrityVerdictResult>>()
        (result as AppResult.Success).data.ageMs shouldBe Long.MAX_VALUE
    }

    @Test
    fun `returns Failure when repository fails to read verdict`() = runTest {
        repository.readError = DomainError.StorageError.DatabaseError("getVerdict", "read error")

        val result = getIntegrityVerdict()
        result.shouldBeInstanceOf<AppResult.Failure<DomainError.StorageError>>()
    }

    @Test
    fun `verdict beyond 24 hours has age greater than 24h threshold`() = runTest {
        val twentyFourHoursMs = 24 * 60 * 60 * 1_000L
        repository.setCurrentTime(0L)
        repository.saveVerdict(IntegrityVerdict.pass(cachedAtMs = 0L))
        repository.setCurrentTime(twentyFourHoursMs + 1L)

        val result = getIntegrityVerdict()
        result.shouldBeInstanceOf<AppResult.Success<IntegrityVerdictResult>>()
        val ageMs = (result as AppResult.Success).data.ageMs
        (ageMs > twentyFourHoursMs) shouldBe true
    }

    @Test
    fun `verdict beyond 72 hours has age greater than 72h threshold`() = runTest {
        val seventyTwoHoursMs = 72 * 60 * 60 * 1_000L
        repository.setCurrentTime(0L)
        repository.saveVerdict(IntegrityVerdict.pass(cachedAtMs = 0L))
        repository.setCurrentTime(seventyTwoHoursMs + 1L)

        val result = getIntegrityVerdict()
        result.shouldBeInstanceOf<AppResult.Success<IntegrityVerdictResult>>()
        val ageMs = (result as AppResult.Success).data.ageMs
        (ageMs > seventyTwoHoursMs) shouldBe true
    }

    @Test
    fun `PASS verdict has all integrity flags set to true`() = runTest {
        repository.saveVerdict(IntegrityVerdict.pass(cachedAtMs = 0L))

        val result = getIntegrityVerdict()
        result.shouldBeInstanceOf<AppResult.Success<IntegrityVerdictResult>>()
        val verdict = (result as AppResult.Success).data.verdict!!
        verdict.isGenuineDevice shouldBe true
        verdict.isAppValid shouldBe true
        verdict.isEnvironmentSafe shouldBe true
    }

    @Test
    fun `FAIL verdict hard-blocks scenario is detected by verdict type`() = runTest {
        repository.saveVerdict(
            IntegrityVerdict.fail(
                isGenuineDevice = false,
                isAppValid = true,
                isEnvironmentSafe = true,
                cachedAtMs = 0L,
            )
        )

        val result = getIntegrityVerdict()
        result.shouldBeInstanceOf<AppResult.Success<IntegrityVerdictResult>>()
        val verdict = (result as AppResult.Success).data.verdict!!
        verdict.verdict shouldBe VerdictType.FAIL
        verdict.isGenuineDevice shouldBe false
    }
}
