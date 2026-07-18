package com.offlinepay.core.domain.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Unit tests for [TransactionFilters].
 */
class TransactionFiltersTest {

    @Test
    fun `empty filters isEmpty is true`() {
        TransactionFilters.none().isEmpty shouldBe true
    }

    @Test
    fun `filters with searchQuery isEmpty is false`() {
        TransactionFilters(searchQuery = "merchant").isEmpty shouldBe false
    }

    @Test
    fun `filters with status isEmpty is false`() {
        TransactionFilters(status = TransactionStatus.SUCCESS).isEmpty shouldBe false
    }

    @Test
    fun `filters with date range isEmpty is false`() {
        TransactionFilters(startDateMs = 1000L, endDateMs = 2000L).isEmpty shouldBe false
    }

    @Test
    fun `filters with amount range isEmpty is false`() {
        TransactionFilters(minAmountPaise = 100L).isEmpty shouldBe false
    }

    @Test
    fun `filters with paymentMethod isEmpty is false`() {
        TransactionFilters(paymentMethod = PaymentMethodType.USSD).isEmpty shouldBe false
    }

    @Test
    fun `none() factory creates truly empty filters`() {
        val filters = TransactionFilters.none()
        filters.searchQuery shouldBe null
        filters.status shouldBe null
        filters.startDateMs shouldBe null
        filters.endDateMs shouldBe null
        filters.minAmountPaise shouldBe null
        filters.maxAmountPaise shouldBe null
        filters.merchantName shouldBe null
        filters.paymentMethod shouldBe null
    }
}
