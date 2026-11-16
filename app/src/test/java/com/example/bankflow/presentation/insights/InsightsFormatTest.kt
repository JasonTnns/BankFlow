package com.example.bankflow.presentation.insights

import com.example.bankflow.domain.model.Category
import com.example.bankflow.testutil.testSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.YearMonth
import java.util.Locale

class InsightsFormatTest {

    private val october = YearMonth.of(2026, 10)
    private val september = YearMonth.of(2026, 9)

    private fun spending(amount: Long) = testSummary(categories = listOf(Category.FOOD to amount))

    @Test
    fun `month titles and names`() {
        assertEquals("October 2026", october.title(Locale.US))
        assertEquals("September", september.monthName(Locale.US))
    }

    @Test
    fun `tiny shares are not shown as zero`() {
        assertEquals("<1%", shareLabel(0))
        assertEquals("1%", shareLabel(1))
        assertEquals("87%", shareLabel(87))
    }

    @Test
    fun `more spending than last month`() {
        assertEquals(
            "10% more than September",
            comparisonText(spending(11_000), spending(10_000), september, Locale.US),
        )
    }

    @Test
    fun `less spending than last month`() {
        assertEquals(
            "25% less than September",
            comparisonText(spending(7_500), spending(10_000), september, Locale.US),
        )
    }

    @Test
    fun `the same spending`() {
        assertEquals(
            "Same as September",
            comparisonText(spending(10_000), spending(10_000), september, Locale.US),
        )
    }

    @Test
    fun `no comparison without something to compare against`() {
        assertNull(comparisonText(spending(10_000), null, september, Locale.US))
        assertNull(comparisonText(spending(10_000), testSummary(categories = emptyList()), september, Locale.US))
    }
}