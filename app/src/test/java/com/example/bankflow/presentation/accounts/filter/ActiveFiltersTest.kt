package com.example.bankflow.presentation.accounts.filter

import com.example.bankflow.domain.model.Category
import com.example.bankflow.domain.model.Direction
import com.example.bankflow.domain.model.TransactionFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class ActiveFiltersTest {

    private val utc = ZoneId.of("UTC")

    private fun TransactionFilter.chips() = activeChips(utc, Locale.US)
    private fun TransactionFilter.labels() = chips().map { it.label }

    @Test
    fun `no filters means no chips, and search is not a chip`() {
        assertTrue(TransactionFilter().chips().isEmpty())
        assertTrue(TransactionFilter(search = "rent").chips().isEmpty())
    }

    @Test
    fun `direction and category chips`() {
        val chips = TransactionFilter(direction = Direction.IN, category = Category.FOOD).chips()

        assertEquals(
            listOf(ActiveFilterChip(FilterKind.DIRECTION, "Money in"), ActiveFilterChip(FilterKind.CATEGORY, "Food")),
            chips,
        )
    }

    @Test
    fun `amount chip reads naturally for a range, a minimum and a maximum`() {
        assertEquals(
            listOf("$25.00 \u2013 $100.00"),
            TransactionFilter(minAmountMinor = 2_500, maxAmountMinor = 10_000).labels(),
        )
        assertEquals(listOf("At least $25.00"), TransactionFilter(minAmountMinor = 2_500).labels())
        assertEquals(listOf("Up to $100.00"), TransactionFilter(maxAmountMinor = 10_000).labels())
    }

    @Test
    fun `date chip shows a range, a single day, or an open end`() {
        val oct1 = Instant.parse("2026-10-01T00:00:00Z")
        val oct29End = Instant.parse("2026-10-29T23:59:59.999Z")
        val oct29 = Instant.parse("2026-10-29T00:00:00Z")

        assertEquals(listOf("Oct 1, 2026 \u2013 Oct 29, 2026"), TransactionFilter(from = oct1, to = oct29End).labels())
        assertEquals(listOf("Oct 29, 2026"), TransactionFilter(from = oct29, to = oct29End).labels())
        assertEquals(listOf("From Oct 1, 2026"), TransactionFilter(from = oct1).labels())
        assertEquals(listOf("Until Oct 29, 2026"), TransactionFilter(to = oct29End).labels())
    }

    @Test
    fun `removing a chip clears only its own restriction`() {
        val full = TransactionFilter(
            direction = Direction.OUT,
            category = Category.BILLS,
            search = "rent",
            from = Instant.parse("2026-10-01T00:00:00Z"),
            to = Instant.parse("2026-10-29T23:59:59.999Z"),
            minAmountMinor = 100,
            maxAmountMinor = 200,
        )

        assertNull(full.without(FilterKind.DIRECTION).direction)
        assertEquals(Category.BILLS, full.without(FilterKind.DIRECTION).category)

        val noDates = full.without(FilterKind.DATE_RANGE)
        assertTrue(noDates.from == null && noDates.to == null)
        assertEquals(100L, noDates.minAmountMinor)

        val noAmounts = full.without(FilterKind.AMOUNT_RANGE)
        assertTrue(noAmounts.minAmountMinor == null && noAmounts.maxAmountMinor == null)

        assertEquals("rent", full.without(FilterKind.CATEGORY).search)
    }
}