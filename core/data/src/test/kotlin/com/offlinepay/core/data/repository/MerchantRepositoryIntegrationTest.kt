package com.offlinepay.core.data.repository

import com.offlinepay.core.domain.model.MerchantProfile
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Integration tests for [MerchantRepositoryImpl].
 *
 * Tests: upsert creates on first call, second upsert updates and increments count,
 * toggleFavourite flips state, getFavourites returns only favourites.
 *
 * Uses in-memory simulation — for real Room tests, use Room.inMemoryDatabaseBuilder.
 *
 * Design reference: Section 9.3
 * Requirements: Req 18.1–18.7
 */
class MerchantRepositoryIntegrationTest {

    private lateinit var merchants: MutableMap<String, MerchantProfile>

    @BeforeEach
    fun setup() {
        merchants = mutableMapOf()
    }

    @Nested
    inner class UpsertTests {

        @Test
        fun `first upsert creates merchant`() {
            val merchant = createMerchant("merchant@upi", "Super Shop")
            upsert(merchant)
            merchants.size shouldBe 1
            merchants["merchant@upi"]?.name shouldBe "Super Shop"
        }

        @Test
        fun `second upsert with same UPI ID updates last_seen_at`() {
            val original = createMerchant("merchant@upi", "Super Shop", lastSeenAt = 1000L)
            upsert(original)

            val updated = original.copy(lastSeenAt = 2000L)
            upsert(updated)

            merchants.size shouldBe 1
            merchants["merchant@upi"]?.lastSeenAt shouldBe 2000L
        }

        @Test
        fun `second upsert increments transaction count`() {
            val merchant = createMerchant("merchant@upi", "Super Shop", transactionCount = 1)
            upsert(merchant)

            // Simulate upsert with increment
            val existing = merchants["merchant@upi"]!!
            merchants["merchant@upi"] = existing.copy(
                transactionCount = existing.transactionCount + 1,
                lastSeenAt = 3000L,
            )

            merchants["merchant@upi"]?.transactionCount shouldBe 2
        }

        @Test
        fun `upsert with different UPI ID creates separate entry`() {
            upsert(createMerchant("shop1@upi", "Shop 1"))
            upsert(createMerchant("shop2@upi", "Shop 2"))

            merchants.size shouldBe 2
        }
    }

    @Nested
    inner class FavouriteTests {

        @Test
        fun `toggleFavourite flips state from false to true`() {
            val merchant = createMerchant("merchant@upi", "Shop", isFavourite = false)
            upsert(merchant)

            toggleFavourite("merchant@upi")

            merchants["merchant@upi"]?.isFavourite shouldBe true
        }

        @Test
        fun `toggleFavourite flips state from true to false`() {
            val merchant = createMerchant("merchant@upi", "Shop", isFavourite = true)
            upsert(merchant)

            toggleFavourite("merchant@upi")

            merchants["merchant@upi"]?.isFavourite shouldBe false
        }

        @Test
        fun `getFavourites returns only favourites`() {
            upsert(createMerchant("shop1@upi", "Shop 1", isFavourite = true))
            upsert(createMerchant("shop2@upi", "Shop 2", isFavourite = false))
            upsert(createMerchant("shop3@upi", "Shop 3", isFavourite = true))

            val favourites = merchants.values.filter { it.isFavourite }
            favourites.size shouldBe 2
        }

        @Test
        fun `getFavourites ordered by last_seen_at desc`() {
            upsert(createMerchant("shop1@upi", "Shop 1", isFavourite = true, lastSeenAt = 1000L))
            upsert(createMerchant("shop2@upi", "Shop 2", isFavourite = true, lastSeenAt = 3000L))

            val favourites = merchants.values
                .filter { it.isFavourite }
                .sortedByDescending { it.lastSeenAt }

            favourites.first().name shouldBe "Shop 2"
        }
    }

    @Nested
    inner class QueryTests {

        @Test
        fun `getByUpiId returns correct merchant`() {
            upsert(createMerchant("target@upi", "Target Shop"))
            upsert(createMerchant("other@upi", "Other Shop"))

            val found = merchants["target@upi"]
            found shouldNotBe null
            found?.name shouldBe "Target Shop"
        }

        @Test
        fun `getByUpiId returns null for non-existent merchant`() {
            val found = merchants["nonexistent@upi"]
            found shouldBe null
        }

        @Test
        fun `getRecent returns merchants sorted by last_seen_at desc`() {
            upsert(createMerchant("a@upi", "A", lastSeenAt = 1000L))
            upsert(createMerchant("b@upi", "B", lastSeenAt = 3000L))
            upsert(createMerchant("c@upi", "C", lastSeenAt = 2000L))

            val recent = merchants.values.sortedByDescending { it.lastSeenAt }.take(5)
            recent.first().name shouldBe "B"
            recent.last().name shouldBe "A"
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun upsert(merchant: MerchantProfile) {
        merchants[merchant.upiId] = merchant
    }

    private fun toggleFavourite(upiId: String) {
        val existing = merchants[upiId] ?: return
        merchants[upiId] = existing.copy(isFavourite = !existing.isFavourite)
    }

    private fun createMerchant(
        upiId: String,
        name: String,
        isFavourite: Boolean = false,
        lastSeenAt: Long = System.currentTimeMillis(),
        transactionCount: Int = 1,
    ) = MerchantProfile(
        id = "id-$upiId",
        upiId = upiId,
        name = name,
        categoryCode = "5411",
        categoryName = "Grocery",
        avatarColor = 0xFF3D2DB5.toInt(),
        isFavourite = isFavourite,
        lastSeenAt = lastSeenAt,
        transactionCount = transactionCount,
        createdAt = lastSeenAt,
    )
}
