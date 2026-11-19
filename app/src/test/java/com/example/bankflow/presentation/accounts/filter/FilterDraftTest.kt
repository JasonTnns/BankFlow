package com.example.bankflow.presentation.accounts.filter

import com.example.bankflow.domain.model.Category
import com.example.bankflow.domain.model.Direction
import com.example.bankflow.domain.model.TransactionFilter
import com.example.bankflow.testutil.toArabicIndic
import com.example.bankflow.testutil.withArabicDecimalSeparator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class FilterDraftTest {

    private val utc = ZoneId.of("UTC")

    private fun FilterDraft.valid(zone: ZoneId = utc): TransactionFilter =
        (toFilter(zone) as FilterDraft.Result.Valid).filter

    private fun FilterDraft.errors(): FilterDraft.Errors =
        (toFilter(utc) as FilterDraft.Result.Invalid).errors

    @Test
    fun `an untouched draft is the empty filter`() {
        assertEquals(TransactionFilter(), FilterDraft().valid())
    }

    @Test
    fun `direction and category carry over, and search is never set by the sheet`() {
        val filter = FilterDraft(direction = Direction.OUT, category = Category.FOOD).valid()

        assertEquals(Direction.OUT, filter.direction)
        assertEquals(Category.FOOD, filter.category)
        assertNull(filter.search)
    }

    @Test
    fun `amounts are parsed exactly into cents`() {
        val filter = FilterDraft(minAmountText = " 25 ", maxAmountText = "100.5").valid()

        assertEquals(2_500L, filter.minAmountMinor)
        assertEquals(10_050L, filter.maxAmountMinor)
    }

    @Test
    fun `blank amounts mean no limit`() {
        val filter = FilterDraft(minAmountText = "", maxAmountText = "   ").valid()

        assertNull(filter.minAmountMinor)
        assertNull(filter.maxAmountMinor)
    }

    @Test
    fun `a bad amount is reported on its own field and nothing is applied`() {
        listOf("abc", "-5", "12.345", "1,000", "1e3").forEach { text ->
            assertTrue("'$text' should be rejected", FilterDraft(minAmountText = text).errors().minAmount != null)
            assertTrue("'$text' should be rejected", FilterDraft(maxAmountText = text).errors().maxAmount != null)
        }
        assertNull(FilterDraft(minAmountText = "abc").errors().maxAmount)
    }

    @Test
    fun `a maximum below the minimum is rejected, an equal one is fine`() {
        assertEquals(
            "Maximum must be at least the minimum",
            FilterDraft(minAmountText = "50", maxAmountText = "10").errors().maxAmount,
        )
        assertEquals(5_000L, FilterDraft(minAmountText = "50", maxAmountText = "50").valid().maxAmountMinor)
    }

    @Test
    fun `a date range covers whole days, through the last millisecond of the end day`() {
        val filter = FilterDraft(fromDate = LocalDate.of(2026, 10, 1), toDate = LocalDate.of(2026, 10, 29)).valid()

        assertEquals(Instant.parse("2026-10-01T00:00:00Z"), filter.from)
        assertEquals(Instant.parse("2026-10-29T23:59:59.999Z"), filter.to)
    }

    @Test
    fun `days are the user's days, not UTC days`() {
        val tokyo = ZoneId.of("Asia/Tokyo") // UTC+9
        val filter = FilterDraft(fromDate = LocalDate.of(2026, 10, 1), toDate = LocalDate.of(2026, 10, 1)).valid(tokyo)

        assertEquals(Instant.parse("2026-09-30T15:00:00Z"), filter.from)
        assertEquals(Instant.parse("2026-10-01T14:59:59.999Z"), filter.to)
    }

    @Test
    fun `opening the sheet on an applied filter and applying it changes nothing`() {
        val applied = TransactionFilter(
            direction = Direction.IN,
            category = Category.INCOME,
            from = Instant.parse("2026-10-01T00:00:00Z"),
            to = Instant.parse("2026-10-29T23:59:59.999Z"),
            minAmountMinor = 2_550,
            maxAmountMinor = 100_000,
        )

        val roundTrip = FilterDraft.from(applied, utc).valid()

        assertEquals(applied, roundTrip)
    }

    @Test
    fun `amount text from an applied filter is plain and exact`() {
        val draft = FilterDraft.from(TransactionFilter(minAmountMinor = 100_000, maxAmountMinor = 2_550), utc)

        assertEquals("1000", draft.minAmountText)
        assertEquals("25.5", draft.maxAmountText)
    }

    @Test
    fun `the date picker's UTC midnight maps to the same calendar day`() {
        assertEquals(86_400_000L, localDateToPickerMillis(LocalDate.of(1970, 1, 2)))
        val day = LocalDate.of(2026, 10, 29)
        assertEquals(day, pickerMillisToLocalDate(localDateToPickerMillis(day)))
    }

    @Test
    fun `amounts typed in another script are parsed`() {
        val filter = FilterDraft(
            minAmountText = "25".toArabicIndic(),
            maxAmountText = "100.5".toArabicIndic().withArabicDecimalSeparator(),
        ).valid()

        assertEquals(2_500L, filter.minAmountMinor)
        assertEquals(10_050L, filter.maxAmountMinor)
    }
}