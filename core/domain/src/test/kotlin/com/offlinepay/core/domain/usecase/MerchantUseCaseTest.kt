package com.offlinepay.core.domain.usecase

import com.offlinepay.core.common.result.AppResult
import com.offlinepay.core.domain.fake.FakeMerchantRepository
import com.offlinepay.core.domain.model.PaymentParams
import com.offlinepay.core.domain.model.QrType
import com.offlinepay.core.domain.usecase.merchant.GetFavouriteMerchantsUseCase
import com.offlinepay.core.domain.usecase.merchant.GetMerchantByUpiIdUseCase
import com.offlinepay.core.domain.usecase.merchant.GetRecentMerchantsUseCase
import com.offlinepay.core.domain.usecase.merchant.ToggleMerchantFavouriteUseCase
import com.offlinepay.core.domain.usecase.merchant.UpsertMerchantUseCase
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for merchant use cases using [FakeMerchantRepository].
 */
class MerchantUseCaseTest {

    private lateinit var repository: FakeMerchantRepository
    private lateinit var upsertUseCase: UpsertMerchantUseCase
    private lateinit var getRecentUseCase: GetRecentMerchantsUseCase
    private lateinit var getFavouritesUseCase: GetFavouriteMerchantsUseCase
    private lateinit var toggleFavouriteUseCase: ToggleMerchantFavouriteUseCase
    private lateinit var getMerchantByUpiIdUseCase: GetMerchantByUpiIdUseCase

    private val params = PaymentParams(
        upiId = "shop@upi",
        payeeName = "Test Shop",
        qrType = QrType.MERCHANT,
    )

    @BeforeEach
    fun setUp() {
        repository = FakeMerchantRepository()
        upsertUseCase = UpsertMerchantUseCase(repository)
        getRecentUseCase = GetRecentMerchantsUseCase(repository)
        getFavouritesUseCase = GetFavouriteMerchantsUseCase(repository)
        toggleFavouriteUseCase = ToggleMerchantFavouriteUseCase(repository)
        getMerchantByUpiIdUseCase = GetMerchantByUpiIdUseCase { upiId ->
            repository.getMerchantByUpiId(upiId)
        }
    }

    @Test
    fun `upsert creates new merchant on first payment`() = runTest {
        val result = upsertUseCase(params, 1_000_000L)
        result.shouldBeInstanceOf<AppResult.Success<*>>()
        repository.merchants.size shouldBe 1
        repository.merchants.first().upiId shouldBe "shop@upi"
    }

    @Test
    fun `upsert increments transaction count on second payment`() = runTest {
        upsertUseCase(params, 1_000_000L)
        upsertUseCase(params, 2_000_000L)
        repository.merchants.first().transactionCount shouldBe 2
    }

    @Test
    fun `upsert updates lastSeenAt on second payment`() = runTest {
        upsertUseCase(params, 1_000_000L)
        upsertUseCase(params, 2_000_000L)
        repository.merchants.first().lastSeenAt shouldBe 2_000_000L
    }

    @Test
    fun `getRecent returns merchants sorted by lastSeenAt`() = runTest {
        upsertUseCase(params.copy(upiId = "a@upi"), 1_000L)
        upsertUseCase(params.copy(upiId = "b@upi"), 3_000L)
        upsertUseCase(params.copy(upiId = "c@upi"), 2_000L)
        val recent = getRecentUseCase(5).first()
        recent.first().upiId shouldBe "b@upi"
        recent.last().upiId shouldBe "a@upi"
    }

    @Test
    fun `getRecent respects limit`() = runTest {
        repeat(10) { i -> upsertUseCase(params.copy(upiId = "merchant$i@upi"), i.toLong()) }
        val recent = getRecentUseCase(5).first()
        recent.size shouldBe 5
    }

    @Test
    fun `getFavourites returns empty when no favourites`() = runTest {
        upsertUseCase(params, 1_000L)
        val favourites = getFavouritesUseCase().first()
        favourites shouldBe emptyList()
    }

    @Test
    fun `toggleFavourite marks merchant as favourite`() = runTest {
        upsertUseCase(params, 1_000L)
        val result = toggleFavouriteUseCase("shop@upi")
        result.shouldBeInstanceOf<AppResult.Success<Unit>>()
        repository.merchants.first().isFavourite shouldBe true
    }

    @Test
    fun `getFavourites returns merchant after toggle`() = runTest {
        upsertUseCase(params, 1_000L)
        toggleFavouriteUseCase("shop@upi")
        val favourites = getFavouritesUseCase().first()
        favourites.size shouldBe 1
        favourites.first().isFavourite shouldBe true
    }

    @Test
    fun `toggleFavourite un-marks a favourite`() = runTest {
        upsertUseCase(params, 1_000L)
        toggleFavouriteUseCase("shop@upi") // mark
        toggleFavouriteUseCase("shop@upi") // unmark
        repository.merchants.first().isFavourite shouldBe false
    }

    @Test
    fun `getMerchantByUpiId returns null when merchant does not exist`() = runTest {
        val result = getMerchantByUpiIdUseCase("unknown@upi")
        result.shouldBeInstanceOf<AppResult.Success<*>>()
        (result as AppResult.Success).data shouldBe null
    }

    @Test
    fun `getMerchantByUpiId returns merchant when it exists`() = runTest {
        upsertUseCase(params, 1_000L)
        val result = getMerchantByUpiIdUseCase("shop@upi")
        result.shouldBeInstanceOf<AppResult.Success<*>>()
        (result as AppResult.Success).data?.upiId shouldBe "shop@upi"
    }
}
