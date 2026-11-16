package com.example.bankflow.domain.model

import com.example.bankflow.testutil.testSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpendingSummaryTest {

    @Test
    fun `a category's share is its part of all spending, in whole percent`() {
        val summary = testSummary(categories = listOf(Category.FOOD to 6_500, Category.BILLS to 1_000))

        assertEquals(87, summary.sharePercent(summary.byCategory[0]))
        assertEquals(13, summary.sharePercent(summary.byCategory[1]))
    }

    @Test
    fun `a share is zero when nothing was spent`() {
        val summary = testSummary(categories = emptyList())

        assertEquals(0, summary.sharePercent(CategorySpend(Category.FOOD, Money(0), 0)))
    }

    @Test
    fun `a summary with no ledger entries is empty`() {
        assertTrue(testSummary(transactionCount = 0, categories = emptyList()).isEmpty)
        assertFalse(testSummary(transactionCount = 1).isEmpty)
    }

    @Test
    fun `percent change is relative to the previous period`() {
        assertEquals(10, percentChange(Money(11_000), Money(10_000)))
        assertEquals(-10, percentChange(Money(9_000), Money(10_000)))
        assertEquals(0, percentChange(Money(10_000), Money(10_000)))
        assertEquals(100, percentChange(Money(20_000), Money(10_000)))
    }

    @Test
    fun `percent change rounds half away from zero`() {
        assertEquals(1, percentChange(Money(10_050), Money(10_000)))
        assertEquals(-1, percentChange(Money(9_950), Money(10_000)))
        assertEquals(0, percentChange(Money(10_049), Money(10_000)))
    }

    @Test
    fun `there is no percentage to give when nothing was spent before`() {
        assertNull(percentChange(Money(5_000), Money(0)))
        assertNull(percentChange(Money(0), Money(0)))
    }
}