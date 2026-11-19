package com.example.bankflow.presentation.insights

import com.example.bankflow.R
import com.example.bankflow.domain.model.Category
import com.example.bankflow.presentation.common.UiText
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
        assertEquals(UiText.Res(R.string.share_less_than_one), shareLabel(0))
        assertEquals(UiText.Res(R.string.share_percent, listOf(1)), shareLabel(1))
        assertEquals(UiText.Res(R.string.share_percent, listOf(87)), shareLabel(87))
    }

    @Test
    fun `more, less and the same spending compared with last month`() {
        assertEquals(
            UiText.Res(R.string.insights_comparison_more, listOf(10, "September")),
            comparisonText(spending(11_000), spending(10_000), september, Locale.US),
        )
        assertEquals(
            UiText.Res(R.string.insights_comparison_less, listOf(25, "September")),
            comparisonText(spending(7_500), spending(10_000), september, Locale.US),
        )
        assertEquals(
            UiText.Res(R.string.insights_comparison_same, listOf("September")),
            comparisonText(spending(10_000), spending(10_000), september, Locale.US),
        )
    }

    @Test
    fun `no comparison without something to compare against`() {
        assertNull(comparisonText(spending(10_000), null, september, Locale.US))
        assertNull(comparisonText(spending(10_000), testSummary(categories = emptyList()), september, Locale.US))
    }
}